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

static jfloatArray project_rows(JNIEnv *env, const float *table, const int8_t *packed,
        const float *scales, jsize rows, jsize columns, jfloatArray input) {
    jfloatArray result = (*env)->NewFloatArray(env, rows);
    if (result == NULL) return NULL;
    jfloat *logits = (*env)->GetFloatArrayElements(env, result, NULL);
    if (logits == NULL) return NULL;
    jfloat *vector = (*env)->GetFloatArrayElements(env, input, NULL);
    if (vector == NULL) {
        (*env)->ReleaseFloatArrayElements(env, result, logits, JNI_ABORT);
        return NULL;
    }
    if (packed != NULL) {
        for (int row = 0; row < rows; ++row) {
            const int8_t *embedding = packed + (size_t)row * columns;
            float sum = 0.0f;
            for (int i = 0; i < columns; ++i) sum += embedding[i] * vector[i];
            logits[row] = sum * scales[row];
        }
    } else {
        for (int row = 0; row < rows; ++row) {
            const float *embedding = table + (size_t)row * columns;
            float sum = 0.0f;
            for (int i = 0; i < columns; ++i) sum += embedding[i] * vector[i];
            logits[row] = sum;
        }
    }
    (*env)->ReleaseFloatArrayElements(env, input, vector, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, result, logits, 0);
    return result;
}

static jfloatArray invalid_dimensions(JNIEnv *env) {
    jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    (*env)->ThrowNew(env, error, "Invalid VieNeu embedding dimensions");
    return NULL;
}

JNIEXPORT jfloatArray JNICALL JNI_NAME(project)(
        JNIEnv *env, jobject instance, jobject weights, jint offset, jint rows, jfloatArray input) {
    const float *table = (*env)->GetDirectBufferAddress(env, weights);
    const jlong capacity = (*env)->GetDirectBufferCapacity(env, weights);
    const jsize columns = (*env)->GetArrayLength(env, input);
    if (table == NULL || offset < 0 || rows < 1 || columns < 1 ||
            (jlong)offset + (jlong)rows * columns > capacity) return invalid_dimensions(env);
    return project_rows(env, table + offset, NULL, NULL, rows, columns, input);
}

JNIEXPORT jfloatArray JNICALL JNI_NAME(projectInt8)(JNIEnv *env, jobject instance, jobject weights,
        jobject row_scales, jint first_row, jint rows, jfloatArray input) {
    const int8_t *table = (*env)->GetDirectBufferAddress(env, weights);
    const float *scales = (*env)->GetDirectBufferAddress(env, row_scales);
    const jsize columns = (*env)->GetArrayLength(env, input);
    const jlong end = (jlong)first_row + rows;
    if (table == NULL || scales == NULL || first_row < 0 || rows < 1 || columns < 1 ||
            end > (*env)->GetDirectBufferCapacity(env, row_scales) ||
            end * columns > (*env)->GetDirectBufferCapacity(env, weights)) return invalid_dimensions(env);
    return project_rows(env, NULL, table + (size_t)first_row * columns, scales + first_row,
                        rows, columns, input);
}
