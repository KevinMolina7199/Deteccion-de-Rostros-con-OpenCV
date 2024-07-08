package ec.edu.ups.proyectofinal;


import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.opencv.android.CameraBridgeViewBase;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class CameraActivity extends org.opencv.android.CameraActivity {
    private CameraBridgeViewBase cameraBridgeViewBase;

    private CascadeClassifier faceCascade, eyeCascade, noseCascade, mouthCascade;
    private Button btnCapture;
    private Mat currentFrame;
    private TextView fpsTextView;
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private long startTime = 0;

    Mat gray,rgb,transpose_gray,transpose_rgb;

    MatOfRect rects;

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

        cameraBridgeViewBase.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                /*lastFrameTime = System.currentTimeMillis();
                startTime = System.currentTimeMillis();*/
                rgb=new Mat();
                gray=new Mat();
                rects=new MatOfRect();
            }

            @Override
            public void onCameraViewStopped() {
                /*if (currentFrame != null) {
                    currentFrame.release();
                }*/
                rgb.release();
                gray.release();
                rects.release();
            }


            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                rgb = inputFrame.rgba();
                gray = inputFrame.gray();

                Core.flip(rgb, rgb, Core.ROTATE_180);
                Core.flip(gray, gray, Core.ROTATE_180);

                transpose_gray = gray.t();
                transpose_rgb = rgb.t();

                detectFacialFeatures(transpose_rgb.getNativeObjAddr());

                return transpose_rgb.t();
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

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureAndReturnImage();
            }
        });
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

            // Guardar el bitmap en un archivo temporal
            File imageFile = saveBitmapToFile(bitmap);

            // Pasar la ruta del archivo a través del intent
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraBridgeViewBase.disableView();
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
}