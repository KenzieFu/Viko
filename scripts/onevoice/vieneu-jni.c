#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "sea_g2p.h"

#define JNI_NAME(method) Java_com_example_viko_data_ai_onevoice_tts_SeaG2p_##method

static char *utf8(JNIEnv *env, jbyteArray bytes) {
    const jsize size = (*env)->GetArrayLength(env, bytes);
    char *text = malloc((size_t)size + 1);
    if (text == NULL) return NULL;
    (*env)->GetByteArrayRegion(env, bytes, 0, size, (jbyte *)text);
    text[size] = '\0';
    return text;
}

JNIEXPORT jlong JNICALL JNI_NAME(openNative)(JNIEnv *env, jobject instance, jbyteArray path) {
    char *name = utf8(env, path);
    if (name == NULL) return 0;
    sea_g2p *handle = sea_g2p_open(name);
    free(name);
    return (jlong)(intptr_t)handle;
}

JNIEXPORT jbyteArray JNICALL JNI_NAME(convertNative)(
        JNIEnv *env, jobject instance, jlong handle, jbyteArray input) {
    char *text = utf8(env, input);
    if (text == NULL) return NULL;
    char *phonemes = sea_g2p_phonemize((sea_g2p *)(intptr_t)handle, text, 1);
    free(text);
    if (phonemes == NULL) return NULL;
    jsize length = (jsize)strlen(phonemes);
    jbyteArray result = (*env)->NewByteArray(env, length);
    if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, length, (jbyte *)phonemes);
    sea_g2p_string_free(phonemes);
    return result;
}

JNIEXPORT void JNICALL JNI_NAME(closeNative)(JNIEnv *env, jobject instance, jlong handle) {
    sea_g2p_close((sea_g2p *)(intptr_t)handle);
}

JNIEXPORT jfloatArray JNICALL JNI_NAME(project)(
        JNIEnv *env, jobject instance, jobject weights, jint offset, jint rows, jfloatArray input) {
    const float *table = (*env)->GetDirectBufferAddress(env, weights);
    const jlong capacity = (*env)->GetDirectBufferCapacity(env, weights);
    const jsize columns = (*env)->GetArrayLength(env, input);
    if (table == NULL || offset < 0 || rows < 1 || columns < 1 ||
            (jlong)offset + (jlong)rows * columns > capacity) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, error, "Invalid VieNeu embedding dimensions");
        return NULL;
    }

    jfloatArray result = (*env)->NewFloatArray(env, rows);
    if (result == NULL) return NULL;
    jfloat *logits = (*env)->GetFloatArrayElements(env, result, NULL);
    if (logits == NULL) return NULL;
    jfloat *vector = (*env)->GetFloatArrayElements(env, input, NULL);
    if (vector == NULL) {
        (*env)->ReleaseFloatArrayElements(env, result, logits, JNI_ABORT);
        return NULL;
    }
    for (int row = 0; row < rows; ++row) {
        const float *embedding = table + offset + (size_t)row * columns;
        float sum = 0.0f;
        for (int i = 0; i < columns; ++i) sum += embedding[i] * vector[i];
        logits[row] = sum;
    }
    (*env)->ReleaseFloatArrayElements(env, input, vector, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, result, logits, 0);
    return result;
}
