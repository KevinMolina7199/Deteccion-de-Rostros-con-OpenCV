package ec.edu.ups.proyectofinal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProcessingActivity extends AppCompatActivity {

    static {
        if (OpenCVLoader.initDebug()) {
            Log.d("MainActivity", "OpenCV is loaded");
        } else {
            Log.d("MainActivity", "OpenCV failed to load");
        }
        System.loadLibrary("native-lib");  // Nombre de la biblioteca debe coincidir con CMakeLists.txt
    }

    private Bitmap bitmapI;
    private Bitmap bitmapO;
    ImageView imageView2;
    Button btnEnviar;
    private Bitmap backgroundBitmap;
    private int gaussianKernelSize = 15; // Tamaño del kernel gaussiano, puedes ajustarlo según sea necesario

    Bitmap selectedBitmap;
    File processedFile;
    int SELETC_CODE = 100, CAMERA_CODE = 101, CAPTURE_IMAGE_REQUEST_CODE = 104;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        imageView2 = findViewById(R.id.imageView2);
        btnEnviar = findViewById(R.id.btn_Enviar);

        String imagePath = getIntent().getStringExtra("capturedImage");
        if (imagePath != null) {
            File imageFile = new File(imagePath);
            try {
                selectedBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                selectedBitmap = rotateImageIfRequired(selectedBitmap, imagePath); // Corregir orientación
                bitmapI = selectedBitmap.copy(Bitmap.Config.ARGB_8888, true);
                bitmapO = Bitmap.createBitmap(bitmapI.getWidth(), bitmapI.getHeight(), Bitmap.Config.ARGB_8888);
                //backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fondo);  // Asegúrate de tener una imagen llamada fondo1 en res/drawable
                backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap, bitmapI.getWidth(), bitmapI.getHeight(), false);

                applyCannyFilter(bitmapI, bitmapO, 50, 150);
                applyLaplacianFilter(bitmapI, bitmapO);
                applyGaussianSobelFilter(bitmapI, bitmapO, gaussianKernelSize);

                //applyGaussianSobelFilterAndCombine(bitmapI, backgroundBitmap, bitmapO, gaussianKernelSize);

                runOnUiThread(() -> imageView2.setImageBitmap(bitmapO));

                // Guardar imagen procesada
                new Thread(() -> {
                    try {
                        processedFile = createImageFile();
                        FileOutputStream fos = new FileOutputStream(processedFile);
                        bitmapO.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        btnEnviar.setOnClickListener(v -> {
            if (processedFile != null) {
                uploadFile(processedFile, "image/jpeg");
            } else {
                Toast.makeText(ProcessingActivity.this, "No se encontró la imagen procesada", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Bitmap rotateImageIfRequired(Bitmap img, String selectedImage) throws IOException {
        ExifInterface ei = new ExifInterface(selectedImage);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

        Log.d("ProcessingActivity", "Original orientation: " + orientation); // Registro de depuración

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                Log.d("ProcessingActivity", "Rotating image 90 degrees"); // Registro de depuración
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                Log.d("ProcessingActivity", "Rotating image 180 degrees"); // Registro de depuración
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                Log.d("ProcessingActivity", "Rotating image 270 degrees"); // Registro de depuración
                return rotateImage(img, 270);
            case ExifInterface.ORIENTATION_NORMAL:
            default:
                Log.d("ProcessingActivity", "No rotation required"); // Registro de depuración
                return img;
        }
    }

    private Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private File createImageFile() throws IOException {
        String imageFileName = "JPEG_" + System.currentTimeMillis() + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    // Método para enviar la imagen al servidor Flask
    private void uploadFile(File file, String mimeType) {
        if (!isNetworkAvailable()) {
            runOnUiThread(() -> Toast.makeText(this, "No hay conexión a Internet", Toast.LENGTH_SHORT).show());
            return;
        }

        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(MediaType.parse(mimeType), file))
                .build();

        Request request = new Request.Builder()
                .url("http://192.168.0.101:1717/upload_mobile") // Cambia esta URL por la de tu servidor Flask
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(ProcessingActivity.this, "Error al subir el archivo", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (!response.isSuccessful()) {
                        Toast.makeText(ProcessingActivity.this, "Error en la respuesta del servidor", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ProcessingActivity.this, "Archivo subido exitosamente", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // Método para verificar la conexión a Internet
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    //private native void applyGaussianSobelFilterAndCombine(Bitmap bitmapIn, Bitmap backgroundBitmap, Bitmap bitmapOut, int gaussianKernelSize);
    private native void applyGaussianSobelFilter(Bitmap bitmapIn, Bitmap bitmapOut, int gaussianKernelSize);
    public native void applyLaplacianFilter(Bitmap bitmapIn, Bitmap bitmapOut);
    public native void applyCannyFilter(Bitmap bitmapIn, Bitmap bitmapOut, int lowThreshold, int highThreshold);
}
