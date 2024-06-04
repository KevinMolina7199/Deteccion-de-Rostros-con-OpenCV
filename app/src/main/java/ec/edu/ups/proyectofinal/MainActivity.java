package ec.edu.ups.proyectofinal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    static {
        //System.loadLibrary("proyecto_vision");
    }

    Button camera, select, btnProcessing;
    ImageView imageView;
    Bitmap bitmap;
    Mat mat;

    Bitmap selectedBitmap;

    private String capturedImagePath;  // Añadir esta variable
    int SELETC_CODE = 100, CAMERA_CODE = 101, CAPTURE_IMAGE_REQUEST_CODE = 104;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (OpenCVLoader.initDebug()) {
            Log.d("MainActivity", "OpenCV is loaded");
        } else {
            Log.d("MainActivity", "OpenCV failed to load");
        }

        getPermissions();

        camera = findViewById(R.id.camera);
        select = findViewById(R.id.select);
        imageView = findViewById(R.id.imageView);
        btnProcessing = findViewById(R.id.btnProcesar);
        select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select.setRotation(0);
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, SELETC_CODE);
            }
        });

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivityForResult(intent, CAMERA_CODE);
            }
        });

        btnProcessing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ProcessingActivity.class);
                intent.putExtra("capturedImage", capturedImagePath);  // Pasar la ruta de la imagen
                startActivityForResult(intent, CAMERA_CODE);
            }
        });
    }

    void getPermissions() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 102);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102 && grantResults.length > 0) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                getPermissions();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELETC_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                try {
                    // Obtener la URI de la imagen seleccionada
                    selectedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());

                    // Mostrar la imagen seleccionada en el ImageView
                    imageView.setImageBitmap(selectedBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == CAMERA_CODE && resultCode == RESULT_OK) {
            if (data != null && data.hasExtra("capturedImage")) {
                capturedImagePath = data.getStringExtra("capturedImage");  // Guardar la ruta de la imagen
                File imageFile = new File(capturedImagePath);
                selectedBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                imageView.setImageBitmap(selectedBitmap);

            }
        }
    }


}