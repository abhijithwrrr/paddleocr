#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <opencv2/imgproc.hpp>
#include <string>
#include <vector>

#include "ocr_engine.h"

namespace {

constexpr const char *kTag = "PaddleOcrJni";

cv::Mat BitmapToBgr(JNIEnv *env, jobject bitmap) {
  if (bitmap == nullptr) return {};

  AndroidBitmapInfo info;
  if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "AndroidBitmap_getInfo failed");
    return {};
  }
  if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Bitmap must be RGBA_8888");
    return {};
  }

  void *pixels = nullptr;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "AndroidBitmap_lockPixels failed");
    return {};
  }

  cv::Mat rgba(info.height, info.width, CV_8UC4, pixels);
  cv::Mat bgr;
  cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);
  AndroidBitmap_unlockPixels(env, bitmap);
  return bgr;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_awbuilds_paddleocr_ocr_PaddleOcrNative_nativeInit(
    JNIEnv *env, jobject /* thiz */,
    jstring detModelPath, jstring recModelPath, jstring clsModelPath,
    jstring configPath, jstring dictPath, jint cpuThreadNum,
    jstring cpuPowerMode) {
  const char *det = env->GetStringUTFChars(detModelPath, nullptr);
  const char *rec = env->GetStringUTFChars(recModelPath, nullptr);
  const char *cls = env->GetStringUTFChars(clsModelPath, nullptr);
  const char *config = env->GetStringUTFChars(configPath, nullptr);
  const char *dict = env->GetStringUTFChars(dictPath, nullptr);
  const char *power = env->GetStringUTFChars(cpuPowerMode, nullptr);

  jlong handle = 0;
  try {
    auto *engine = new OcrEngine(det, rec, cls, config, dict,
                                 static_cast<int>(cpuThreadNum), power);
    handle = reinterpret_cast<jlong>(engine);
  } catch (const std::exception &e) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Init failed: %s", e.what());
  } catch (...) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Init failed with unknown error");
  }

  env->ReleaseStringUTFChars(detModelPath, det);
  env->ReleaseStringUTFChars(recModelPath, rec);
  env->ReleaseStringUTFChars(clsModelPath, cls);
  env->ReleaseStringUTFChars(configPath, config);
  env->ReleaseStringUTFChars(dictPath, dict);
  env->ReleaseStringUTFChars(cpuPowerMode, power);
  return handle;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_awbuilds_paddleocr_ocr_PaddleOcrNative_nativeRecognize(
    JNIEnv *env, jobject /* thiz */, jlong handle, jobject bitmap,
    jint maxSideLen) {
  jclass line_class = env->FindClass("com/awbuilds/paddleocr/ocr/OcrLineNative");
  if (line_class == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "OcrLineNative class not found");
    return nullptr;
  }

  jmethodID constructor =
      env->GetMethodID(line_class, "<init>", "(Ljava/lang/String;F[I)V");
  if (constructor == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "OcrLineNative constructor not found");
    env->DeleteLocalRef(line_class);
    return nullptr;
  }

  jobjectArray result = env->NewObjectArray(0, line_class, nullptr);
  if (handle == 0) {
    env->DeleteLocalRef(line_class);
    return result;
  }

  try {
    auto *engine = reinterpret_cast<OcrEngine *>(handle);
    cv::Mat bgr = BitmapToBgr(env, bitmap);
    if (bgr.empty()) {
      env->DeleteLocalRef(line_class);
      return result;
    }

    auto lines = engine->Recognize(bgr, static_cast<int>(maxSideLen));
    __android_log_print(ANDROID_LOG_INFO, kTag, "nativeRecognize: %zu lines", lines.size());
    result = env->NewObjectArray(static_cast<jsize>(lines.size()), line_class, nullptr);
    for (jsize index = 0; index < static_cast<jsize>(lines.size()); ++index) {
      const auto &line = lines[static_cast<size_t>(index)];

      jstring text = env->NewStringUTF(line.text.c_str());
      jintArray points = env->NewIntArray(static_cast<jsize>(line.points.size() * 2));
      std::vector<jint> flat;
      flat.reserve(line.points.size() * 2);
      for (const auto &point : line.points) {
        flat.push_back(point.size() > 0 ? point[0] : 0);
        flat.push_back(point.size() > 1 ? point[1] : 0);
      }
      env->SetIntArrayRegion(points, 0, static_cast<jsize>(flat.size()), flat.data());

      jobject object = env->NewObject(line_class, constructor, text,
                                      static_cast<jfloat>(line.score), points);
      env->SetObjectArrayElement(result, index, object);

      env->DeleteLocalRef(text);
      env->DeleteLocalRef(points);
      env->DeleteLocalRef(object);
    }
  } catch (const std::exception &e) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Recognition failed: %s", e.what());
  } catch (...) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "Recognition failed with unknown error");
  }

  env->DeleteLocalRef(line_class);
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_awbuilds_paddleocr_ocr_PaddleOcrNative_nativeRelease(
    JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
  if (handle == 0) return;
  auto *engine = reinterpret_cast<OcrEngine *>(handle);
  delete engine;
}
