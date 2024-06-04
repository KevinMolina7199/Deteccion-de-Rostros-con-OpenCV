#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/bitmap.h>

using namespace cv;

void applyGaussianSobelFilter(cv::Mat& src, cv::Mat& dst, int gaussianKernelSize) {
    // Aplicar filtro gaussiano
    cv::Mat blurred;
    cv::GaussianBlur(src, blurred, cv::Size(gaussianKernelSize, gaussianKernelSize), 0);

    // Aplicar el operador Sobel
    cv::Mat sobelX, sobelY, sobelCombined;
    cv::Sobel(blurred, sobelX, CV_16S, 1, 0);
    cv::Sobel(blurred, sobelY, CV_16S, 0, 1);
    cv::convertScaleAbs(sobelX, sobelX);
    cv::convertScaleAbs(sobelY, sobelY);
    cv::addWeighted(sobelX, 0.5, sobelY, 0.5, 0, sobelCombined);

    // Asignar la matriz resultante a la matriz de destino
    dst = sobelCombined.clone();
}


void bitmapToMat(JNIEnv * env, jobject bitmap, cv::Mat &dst, jboolean needUnPremultiplyAlpha){
    AndroidBitmapInfo  info;
    void*              pixels = 0;

    try {
        CV_Assert( AndroidBitmap_getInfo(env, bitmap, &info) >= 0 );
        CV_Assert( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                   info.format == ANDROID_BITMAP_FORMAT_RGB_565 );
        CV_Assert( AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 );
        CV_Assert( pixels );
        dst.create(info.height, info.width, CV_8UC4);
        if( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 )
        {
            cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(needUnPremultiplyAlpha) cvtColor(tmp, dst, cv::COLOR_mRGBA2RGBA);
            else tmp.copyTo(dst);
        } else {
            // info.format == ANDROID_BITMAP_FORMAT_RGB_565
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            cvtColor(tmp, dst, cv::COLOR_BGR5652RGBA);
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch(const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        //jclass je = env->FindClass("org/opencv/core/CvException");
        jclass je = env->FindClass("java/lang/Exception");
        //if(!je) je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nBitmapToMat}");
        return;
    }
}


void matToBitmap(JNIEnv * env, cv::Mat src, jobject bitmap, jboolean needPremultiplyAlpha) {
    AndroidBitmapInfo  info;
    void*              pixels = 0;
    try {
        CV_Assert( AndroidBitmap_getInfo(env, bitmap, &info) >= 0 );
        CV_Assert( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                   info.format == ANDROID_BITMAP_FORMAT_RGB_565 );
        CV_Assert( src.dims == 2 && info.height == (uint32_t)src.rows && info.width == (uint32_t)src.cols );
        CV_Assert( src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4 );
        CV_Assert( AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 );
        CV_Assert( pixels );
        if( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 )
        {
            cv::Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, cv::COLOR_GRAY2RGBA);
            } else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, cv::COLOR_RGB2RGBA);
            } else if(src.type() == CV_8UC4){
                if(needPremultiplyAlpha) cvtColor(src, tmp, cv::COLOR_RGBA2mRGBA);
                else src.copyTo(tmp);
            }
        } else {
            // info.format == ANDROID_BITMAP_FORMAT_RGB_565
            cv::Mat tmp(info.height, info.width, CV_8UC2, pixels);
            if(src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, cv::COLOR_GRAY2BGR565);
            } else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, cv::COLOR_RGB2BGR565);
            } else if(src.type() == CV_8UC4){
                cvtColor(src, tmp, cv::COLOR_RGBA2BGR565);
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch(const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        //jclass je = env->FindClass("org/opencv/core/CvException");
        jclass je = env->FindClass("java/lang/Exception");
        //if(!je) je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nMatToBitmap}");
        return;
    }
}

void applyGaussianSobelFilterAndCombine(cv::Mat& src, cv::Mat& background, cv::Mat& dst, int gaussianKernelSize) {
    // Aplicar filtro Gaussian-Sobel a la imagen de entrada
    cv::Mat filtered;
    applyGaussianSobelFilter(src, filtered, gaussianKernelSize);

    // Rotar la imagen de fondo 90 grados
    cv::Mat rotatedBackground;
    cv::rotate(background, rotatedBackground, cv::ROTATE_90_COUNTERCLOCKWISE);

    // Redimensionar la imagen de fondo si es necesario
    if (rotatedBackground.size() != filtered.size()) {
        cv::resize(rotatedBackground, rotatedBackground, filtered.size());
    }

    // Combinar la imagen filtrada con el fondo
    cv::addWeighted(filtered, 0.5, rotatedBackground, 0.5, 0, dst);

}
void applyLaplacianFilter(cv::Mat& src, cv::Mat& dst) {
    cv::Laplacian(src, dst, CV_16S, 3);
    cv::convertScaleAbs(dst, dst);
}

void applyCannyFilter(cv::Mat& src, cv::Mat& dst, int lowThreshold, int highThreshold) {
    cv::Canny(src, dst, lowThreshold, highThreshold);
}

extern "C"
JNIEXPORT void JNICALL
Java_ec_edu_ups_proyectofinal_ProcessingActivity_applyGaussianSobelFilter(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmapIn,
        jobject bitmapOut,
        jint gaussianKernelSize) {

    cv::Mat src;
    bitmapToMat(env, bitmapIn, src, false);

    // Aplicar el filtro gaussiano seguido del operador Sobel
    cv::Mat result;
    applyGaussianSobelFilter(src, result, gaussianKernelSize);

    // Convertir la matriz resultante de nuevo a bitmap
    matToBitmap(env, result, bitmapOut, false);
}
extern "C"
JNIEXPORT void JNICALL
Java_ec_edu_ups_proyectofinal_ProcessingActivity_applyLaplacianFilter(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmapIn,
        jobject bitmapOut) {

    cv::Mat src;
    bitmapToMat(env, bitmapIn, src, false);

    // Aplicar el filtro Laplaciano
    cv::Mat result;
    applyLaplacianFilter(src, result);

    // Convertir la matriz resultante de nuevo a bitmap
    matToBitmap(env, result, bitmapOut, false);
}
extern "C"
JNIEXPORT void JNICALL
Java_ec_edu_ups_proyectofinal_ProcessingActivity_applyCannyFilter(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmapIn,
        jobject bitmapOut,
        jint lowThreshold,
        jint highThreshold) {

    cv::Mat src;
    bitmapToMat(env, bitmapIn, src, false);

    // Aplicar el filtro Canny
    cv::Mat result;
    applyCannyFilter(src, result, lowThreshold, highThreshold);

    // Convertir la matriz resultante de nuevo a bitmap
    matToBitmap(env, result, bitmapOut, false);
}