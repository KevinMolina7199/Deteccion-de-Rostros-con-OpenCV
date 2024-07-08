#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/bitmap.h>
#include <opencv2/objdetect.hpp>
#include <opencv2/core/types_c.h>



using namespace cv;
using namespace std;

CascadeClassifier face_cascade;
CascadeClassifier eye_cascade;
CascadeClassifier nose_cascade;
CascadeClassifier mouth_cascade;


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

void detectMouth(Mat& img, Rect face)
{
    vector<Rect> mouths;
    Mat faceROI = img(Rect(face.x, face.y + face.height * 2 / 3, face.width, face.height / 3));
    mouth_cascade.detectMultiScale(faceROI, mouths, 1.15, 4, 0, Size(25, 15));
    for (size_t i = 0; i < mouths.size(); i++)
    {
        Rect mouth = mouths[i];
        rectangle(img, Point(face.x + mouth.x, face.y + mouth.y + face.height * 2 / 3),
                  Point(face.x + mouth.x + mouth.width, face.y + mouth.y + mouth.height + face.height * 2 / 3),
                  Scalar(0, 255, 0), 2);
    }
}

void detectNose(Mat& img, Rect face)
{
    vector<Rect> noses;
    Mat faceROI = img(Rect(face.x + face.width / 4, face.y + face.height / 3, face.width / 2, face.height / 3));
    nose_cascade.detectMultiScale(faceROI, noses, 1.15, 4, 0, Size(25, 15));
    for (size_t i = 0; i < noses.size(); i++)
    {
        Rect nose = noses[i];
        rectangle(img, Point(face.x + nose.x + face.width / 4, face.y + nose.y + face.height / 3),
                  Point(face.x + nose.x + nose.width + face.width / 4, face.y + nose.y + nose.height + face.height / 3),
                  Scalar(255, 0, 0), 2);
    }
}

void detectEyes(Mat& img, Rect face)
{
    vector<Rect> eyes;
    Mat faceROI = img(Rect(face.x + face.width / 8, face.y, face.width * 3 / 4, face.height / 2));
    eye_cascade.detectMultiScale(faceROI, eyes, 1.15, 4, 0, Size(25, 15));
    for (size_t j = 0; j < eyes.size(); j++)
    {
        Rect eye = eyes[j];
        rectangle(img, Point(face.x + eye.x + face.width / 8, face.y + eye.y),
                  Point(face.x + eye.x + eye.width + face.width / 8, face.y + eye.y + eye.height),
                  Scalar(0, 0, 255), 2);
    }
}


void detectAndDraw(Mat& img)
{
    vector<Rect> faces;
    Mat gray;
    cvtColor(img, gray, COLOR_BGR2GRAY);
    equalizeHist(gray, gray);
    face_cascade.detectMultiScale(gray, faces, 1.1, 2, 0 | CASCADE_SCALE_IMAGE, Size(30, 30));
    for (size_t i = 0; i < faces.size(); i++)
    {
        Rect face = faces[i];
        rectangle(img, Point(face.x, face.y), Point(face.x + face.width, face.y + face.height), Scalar(255, 0, 255), 2);
        detectEyes(img, face);
        detectNose(img, face);
        detectMouth(img, face);
    }
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

extern "C" JNIEXPORT void JNICALL Java_ec_edu_ups_proyectofinal_CameraActivity_detectFacialFeatures
        (JNIEnv* env, jobject, jlong addrInput)
{
    Mat& img = *(Mat*)addrInput;
    detectAndDraw(img);
}


extern "C" JNIEXPORT void JNICALL Java_ec_edu_ups_proyectofinal_CameraActivity_loadCascadeFiles
        (JNIEnv* env, jobject, jstring faceCascadePath, jstring eyeCascadePath, jstring noseCascadePath, jstring mouthCascadePath)
{
    const char* faceCascadePathStr = env->GetStringUTFChars(faceCascadePath, nullptr);
    const char* eyeCascadePathStr = env->GetStringUTFChars(eyeCascadePath, nullptr);
    const char* noseCascadePathStr = env->GetStringUTFChars(noseCascadePath, nullptr);
    const char* mouthCascadePathStr = env->GetStringUTFChars(mouthCascadePath, nullptr);

    face_cascade.load(faceCascadePathStr);
    eye_cascade.load(eyeCascadePathStr);
    nose_cascade.load(noseCascadePathStr);
    mouth_cascade.load(mouthCascadePathStr);

    env->ReleaseStringUTFChars(faceCascadePath, faceCascadePathStr);
    env->ReleaseStringUTFChars(eyeCascadePath, eyeCascadePathStr);
    env->ReleaseStringUTFChars(noseCascadePath, noseCascadePathStr);
    env->ReleaseStringUTFChars(mouthCascadePath, mouthCascadePathStr);
}
