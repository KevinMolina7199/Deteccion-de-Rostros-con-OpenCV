package ec.edu.ups.proyectofinal;


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

    CascadeClassifier cascadeClassifier;
    private Button btnCapture;
    private Mat currentFrame;
    private TextView fpsTextView;
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private long startTime = 0;

    Mat gray,rgb,transpose_gray,transpose_rgb;

    MatOfRect rects;
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
                /*currentFrame = inputFrame.rgba();
                calculateAndDisplayFPS();
                return currentFrame;*/


                rgb=inputFrame.rgba();
                gray=inputFrame.gray();

                //Core.flip();

                Core.flip(rgb,rgb,Core.ROTATE_180);
                Core.flip(gray,gray,Core.ROTATE_180);

                transpose_gray=gray.t();
                transpose_rgb=rgb.t();


                MatOfRect rects=new MatOfRect();

                cascadeClassifier.detectMultiScale(transpose_rgb,rects,1.1,2);

                for (Rect rect: rects.toList()){

                    Mat submat=transpose_rgb.submat(rect);

                    Imgproc.blur(submat,submat,new Size(10,10));
                    Imgproc.rectangle(transpose_rgb,rect,new Scalar(0,255,0),10);

                    submat.release();
                }

                return transpose_rgb.t();
            }

        });

        if (OpenCVLoader.initDebug()) {
            cameraBridgeViewBase.enableView();

            try {
                InputStream inputStream=getResources().openRawResource(R.raw.lbpcascade_frontalface);
                File file=new File(getDir("cascade",MODE_PRIVATE),"lbpcascade_frontalface.xml");
                FileOutputStream fileOutputStream=new FileOutputStream(file);

                byte[] data=new byte[4096];
                int read_bytes;

                while ((read_bytes=inputStream.read(data))!=-1){
                    fileOutputStream.write(data,0,read_bytes);

                }

                cascadeClassifier=new CascadeClassifier(file.getAbsolutePath());
                if (cascadeClassifier.empty()) cascadeClassifier=null;

                inputStream.close();
                fileOutputStream.close();
                file.delete();

            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
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

}