package ec.edu.ups.proyectofinal;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import okio.ByteString;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.*;
import java.io.File;
import java.io.IOException;

public class CameraActivity extends org.opencv.android.CameraActivity {

    private static final String TAG = "CameraActivity";
    private JavaCameraView cameraView;

    private WebSocketClient webSocketClient;
    private CameraBridgeViewBase cameraBridgeViewBase;

    private CascadeClassifier faceCascade, eyeCascade, noseCascade, mouthCascade;
    private Button btnCapture;
    private TextView fpsTextView;

    private long lastFrameTime = 0;
    private long startTime = 0;
    private int frameCount = 0;

    private static final int FPS_LIMIT = 10; // Limit to 10 FPS
    private long frameInterval = 1000 / FPS_LIMIT;

    private Mat gray, rgb, transpose_gray, transpose_rgb;
    private MatOfRect rects;

    private Bitmap glassesBitmap;

    // Declara la variable currentFrame
    private Mat currentFrame;

    static {
        System.loadLibrary("native-lib");
    }

    private native void detectFacialFeatures(long addrInput);
    private native void loadCascadeFiles(String faceCascadePath, String eyeCascadePath, String noseCascadePath, String mouthCascadePath);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        cameraBridgeViewBase = findViewById(R.id.cameraView);
        btnCapture = findViewById(R.id.btnCapture);
        fpsTextView = findViewById(R.id.fpsTextView);

        // Cargar la imagen de las gafas
        glassesBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.gafas);

        cameraBridgeViewBase.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                rgb = new Mat();
                gray = new Mat();
                rects = new MatOfRect();
            }

            @Override
            public void onCameraViewStopped() {
                rgb.release();
                gray.release();
                rects.release();
            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFrameTime < frameInterval) {
                    return null; // Skip the frame
                }
                lastFrameTime = currentTime;

                rgb = inputFrame.rgba();
                gray = inputFrame.gray();

                Core.flip(rgb, rgb, Core.ROTATE_180);
                Core.flip(gray, gray, Core.ROTATE_180);

                transpose_gray = gray.t();
                transpose_rgb = rgb.t();

                detectFacialFeatures(transpose_rgb.getNativeObjAddr());

                Mat outputFrame = transpose_rgb.t();
                addGlassesToEyes(outputFrame);

                // Asigna el valor a currentFrame
                currentFrame = outputFrame.clone();

                // Enviar el fotograma al servidor
                sendFrameToServer(outputFrame);

                return outputFrame;
            }
        });

        if (OpenCVLoader.initDebug()) {
            cameraBridgeViewBase.enableView();

            File faceCascadeFile = loadCascadeFile(R.raw.lbpcascade_frontalface, "lbpcascade_frontalface.xml");
            File eyeCascadeFile = loadCascadeFile(R.raw.haarcascade_eye, "haarcascade_eye.xml");
            File noseCascadeFile = loadCascadeFile(R.raw.haarcascade_mcs_nose, "haarcascade_mcs_nose.xml");
            File mouthCascadeFile = loadCascadeFile(R.raw.haarcascade_mcs_mouth, "haarcascade_mcs_mouth.xml");

            if (faceCascadeFile != null && eyeCascadeFile != null && noseCascadeFile != null && mouthCascadeFile != null) {
                loadCascadeFiles(faceCascadeFile.getAbsolutePath(), eyeCascadeFile.getAbsolutePath(), noseCascadeFile.getAbsolutePath(), mouthCascadeFile.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to load cascade files");
            }
        }

        btnCapture.setOnClickListener(v -> captureAndReturnImage());

        // Initialize WebSocket client
        webSocketClient = new WebSocketClient("ws://192.168.18.128:5000/upload");
        webSocketClient.connect();
    }

    private void calculateAndDisplayFPS() {
        long currentTime = System.currentTimeMillis();
        frameCount++;

        if (currentTime - startTime >= 1000) {
            double fps = (frameCount * 1000.0) / (currentTime - startTime);
            fpsTextView.setText(String.format("FPS: %.2f", fps));
            frameCount = 0;
            startTime = currentTime;
        }
    }

    private void captureAndReturnImage() {
        if (currentFrame != null) {
            Bitmap bitmap = Bitmap.createBitmap(currentFrame.cols(), currentFrame.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(currentFrame, bitmap);

            File imageFile = saveBitmapToFile(bitmap);

            Intent resultIntent = new Intent();
            resultIntent.putExtra("capturedImage", imageFile.getAbsolutePath());
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            cameraBridgeViewBase.enableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraBridgeViewBase.disableView();
        webSocketClient.close();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraBridgeViewBase.disableView();
        webSocketClient.close();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }

    private File saveBitmapToFile(Bitmap bitmap) {
        File imageFile = null;
        try {
            imageFile = File.createTempFile("image", ".png", getCacheDir());
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageFile;
    }

    private File loadCascadeFile(int resourceId, String fileName) {
        try {
            InputStream is = getResources().openRawResource(resourceId);
            File cascadeDir = getDir("cascade", MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, fileName);
            FileOutputStream os = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            return cascadeFile;
        } catch (Exception e) {
            Log.e(TAG, "Error loading cascade file: " + fileName, e);
            return null;
        }
    }

    private void addGlassesToEyes(Mat frame) {
        if (eyeCascade == null) {
            Log.e(TAG, "eyeCascade is null");
            return;
        }
        MatOfRect eyes = new MatOfRect();
        eyeCascade.detectMultiScale(frame, eyes, 1.1, 2, 0, new Size(30, 30), new Size());

        for (Rect eye : eyes.toArray()) {
            Mat roi = frame.submat(eye);
            Bitmap roiBitmap = Bitmap.createBitmap(roi.cols(), roi.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(roi, roiBitmap);

            Bitmap scaledGlasses = Bitmap.createScaledBitmap(glassesBitmap, roi.cols(), roi.rows(), false);

            Canvas canvas = new Canvas(roiBitmap);
            Paint paint = new Paint();
            canvas.drawBitmap(scaledGlasses, 0, 0, paint);

            Utils.bitmapToMat(roiBitmap, roi);
            roi.release();
        }
    }

    private void sendFrameToServer(Mat frame) {
        // Convertir Mat a Bitmap
        Bitmap bitmap = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frame, bitmap);

        // Convertir Bitmap a JPEG y luego a Base64
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP);

        // Crear JSON para el mensaje
        JSONObject jsonMessage = new JSONObject();
        try {
            jsonMessage.put("image", base64Image);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Enviar la imagen en Base64 al servidor usando HTTP POST
        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonMessage.toString()
        );

        Request request = new Request.Builder()
                .url("http://192.168.18.128:5000/upload_detection")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                // Manejar respuesta si es necesario
            }
        });
    }


    private class WebSocketClient {
        private static final String TAG = "WebSocketClient";
        private WebSocket webSocket;
        private String serverUrl;
        private OkHttpClient client;

        public WebSocketClient(String serverUrl) {
            this.serverUrl = serverUrl;
            client = new OkHttpClient();
        }

        public void connect() {
            Request request = new Request.Builder().url(serverUrl).build();

            webSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                    Log.d(TAG, "WebSocket connected");
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    Log.d(TAG, "WebSocket message received: " + text);
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    Log.d(TAG, "WebSocket bytes message received");
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    Log.d(TAG, "WebSocket closing: " + reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    Log.d(TAG, "WebSocket closed: " + reason);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                    Log.e(TAG, "WebSocket error: " + t.getMessage(), t);
                }
            });
        }

        public void send(ByteString byteString) {
            if (webSocket != null) {
                webSocket.send(byteString);
            }
        }

        public void close() {
            if (webSocket != null) {
                webSocket.close(1000, "Closing");
            }
        }
    }
}
