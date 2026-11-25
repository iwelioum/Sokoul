
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <curl/curl.h>

#ifdef __ANDROID__
#include <android/multinetwork.h>
#endif
#define SOKOUL_JNI(name) Java_com_sokoul_desktop_player_CurlImpersonate_##name

#define BODY_CHUNK_BYTES (64 * 1024)

typedef struct {
    JNIEnv *env;
    jobject callback;
    jmethodID on_header_line;
    jmethodID on_body;
    jmethodID should_abort;
    jbyteArray chunk;
    jlong network;
    int java_failed;
} sokoul_transfer;

static size_t on_header(char *data, size_t size, size_t count, void *userdata) {
    sokoul_transfer *t = userdata;
    JNIEnv *env = t->env;
    size_t length = size * count;
    if (t->java_failed) return 0;

    jbyteArray line = (*env)->NewByteArray(env, (jsize) length);
    if (line == NULL) {
        t->java_failed = 1;
        return 0;
    }
    (*env)->SetByteArrayRegion(env, line, 0, (jsize) length, (const jbyte *) data);
    (*env)->CallVoidMethod(env, t->callback, t->on_header_line, line);
    (*env)->DeleteLocalRef(env, line);
    if ((*env)->ExceptionCheck(env)) {
        t->java_failed = 1;
        return 0;
    }
    return length;
}

static size_t on_body(char *data, size_t size, size_t count, void *userdata) {
    sokoul_transfer *t = userdata;
    JNIEnv *env = t->env;
    size_t length = size * count;
    size_t offset = 0;
    if (t->java_failed) return 0;

    while (offset < length) {
        size_t remaining = length - offset;
        jint part = (jint) (remaining > BODY_CHUNK_BYTES ? BODY_CHUNK_BYTES : remaining);
        (*env)->SetByteArrayRegion(env, t->chunk, 0, part, (const jbyte *) data + offset);
        jboolean keep = (*env)->CallBooleanMethod(env, t->callback, t->on_body, t->chunk, part);
        if ((*env)->ExceptionCheck(env)) {
            t->java_failed = 1;
            return 0;
        }
        if (!keep) return 0;
        offset += (size_t) part;
    }
    return length;
}

static int on_progress(void *userdata, curl_off_t dltotal, curl_off_t dlnow,
                       curl_off_t ultotal, curl_off_t ulnow) {
    (void) dltotal;
    (void) dlnow;
    (void) ultotal;
    (void) ulnow;
    sokoul_transfer *t = userdata;
    JNIEnv *env = t->env;
    if (t->java_failed) return 1;
    jboolean abort = (*env)->CallBooleanMethod(env, t->callback, t->should_abort);
    if ((*env)->ExceptionCheck(env)) {
        t->java_failed = 1;
        return 1;
    }
    return abort ? 1 : 0;
}

static int on_socket(void *userdata, curl_socket_t fd, curlsocktype purpose) {
    sokoul_transfer *t = userdata;
#ifdef __ANDROID__
    if (purpose == CURLSOCKTYPE_IPCXN && t->network != 0 &&
        android_setsocknetwork((net_handle_t) t->network, fd) != 0) {
        return CURL_SOCKOPT_ERROR;
    }
#else
    (void) t;
    (void) fd;
    (void) purpose;
#endif
    return CURL_SOCKOPT_OK;
}

static struct curl_slist *to_slist(JNIEnv *env, jobjectArray values, int *failed) {
    struct curl_slist *list = NULL;
    if (values == NULL) return NULL;
    jsize count = (*env)->GetArrayLength(env, values);
    for (jsize i = 0; i < count; i++) {
        jstring value = (jstring) (*env)->GetObjectArrayElement(env, values, i);
        if (value == NULL) continue;
        const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
        if (chars == NULL) {
            *failed = 1;
            break;
        }
        struct curl_slist *next = curl_slist_append(list, chars);
        (*env)->ReleaseStringUTFChars(env, value, chars);
        (*env)->DeleteLocalRef(env, value);
        if (next == NULL) {
            *failed = 1;
            break;
        }
        list = next;
    }
    return list;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) vm;
    (void) reserved;
    if (curl_global_init(CURL_GLOBAL_DEFAULT) != CURLE_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL
SOKOUL_JNI(nativeCreate)(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return (jlong) (intptr_t) curl_easy_init();
}

JNIEXPORT void JNICALL
SOKOUL_JNI(nativeDestroy)(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env;
    (void) clazz;
    if (handle != 0) curl_easy_cleanup((CURL *) (intptr_t) handle);
}

JNIEXPORT jstring JNICALL
SOKOUL_JNI(nativeErrorText)(JNIEnv *env, jclass clazz, jint code) {
    (void) clazz;
    return (*env)->NewStringUTF(env, curl_easy_strerror((CURLcode) code));
}

JNIEXPORT jint JNICALL
SOKOUL_JNI(nativePerform)(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jstring target,
    jstring url,
    jboolean head,
    jobjectArray headers,
    jobjectArray resolve,
    jstring acceptEncoding,
    jstring caFile,
    jlong network,
    jint connectTimeoutSec,
    jint stallTimeoutSec,
    jobject callback) {
    (void) clazz;
    CURL *curl = (CURL *) (intptr_t) handle;
    if (curl == NULL || target == NULL || url == NULL || caFile == NULL || callback == NULL) {
        return CURLE_BAD_FUNCTION_ARGUMENT;
    }

    jclass callbackClass = (*env)->GetObjectClass(env, callback);
    sokoul_transfer transfer = {
        .env = env,
        .callback = callback,
        .on_header_line = (*env)->GetMethodID(env, callbackClass, "onHeaderLine", "([B)V"),
        .on_body = (*env)->GetMethodID(env, callbackClass, "onBody", "([BI)Z"),
        .should_abort = (*env)->GetMethodID(env, callbackClass, "shouldAbort", "()Z"),
        .chunk = (*env)->NewByteArray(env, BODY_CHUNK_BYTES),
        .network = network,
        .java_failed = 0,
    };
    (*env)->DeleteLocalRef(env, callbackClass);
    if (transfer.on_header_line == NULL || transfer.on_body == NULL ||
        transfer.should_abort == NULL || transfer.chunk == NULL) {
        if (transfer.chunk != NULL) (*env)->DeleteLocalRef(env, transfer.chunk);
        return CURLE_FAILED_INIT;
    }

    int failed = 0;
    struct curl_slist *headerList = to_slist(env, headers, &failed);
    struct curl_slist *resolveList = failed ? NULL : to_slist(env, resolve, &failed);
    const char *targetChars = (*env)->GetStringUTFChars(env, target, NULL);
    const char *urlChars = (*env)->GetStringUTFChars(env, url, NULL);
    const char *caChars = (*env)->GetStringUTFChars(env, caFile, NULL);
    const char *encodingChars = acceptEncoding == NULL
        ? NULL
        : (*env)->GetStringUTFChars(env, acceptEncoding, NULL);
    char errorText[CURL_ERROR_SIZE];
    errorText[0] = '\0';

    CURLcode code = CURLE_FAILED_INIT;
    if (!failed && targetChars != NULL && urlChars != NULL && caChars != NULL &&
        (acceptEncoding == NULL || encodingChars != NULL)) {
        curl_easy_reset(curl);
        code = curl_easy_impersonate(curl, targetChars, 0);
        if (code == CURLE_OK) {
            curl_easy_setopt(curl, CURLOPT_URL, urlChars);
            if (head) {
                curl_easy_setopt(curl, CURLOPT_NOBODY, 1L);
            } else {
                curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
            }
            curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headerList);
            if (encodingChars != NULL) curl_easy_setopt(curl, CURLOPT_ACCEPT_ENCODING, encodingChars);
            curl_easy_setopt(curl, CURLOPT_RESOLVE, resolveList);
            curl_easy_setopt(curl, CURLOPT_PROTOCOLS_STR, "https");
            curl_easy_setopt(curl, CURLOPT_REDIR_PROTOCOLS_STR, "https");
            curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 0L);
            curl_easy_setopt(curl, CURLOPT_PROXY, "");
            curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
            curl_easy_setopt(curl, CURLOPT_TCP_KEEPALIVE, 1L);
            curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, (long) connectTimeoutSec);
            curl_easy_setopt(curl, CURLOPT_LOW_SPEED_LIMIT, 1L);
            curl_easy_setopt(curl, CURLOPT_LOW_SPEED_TIME, (long) stallTimeoutSec);
            curl_easy_setopt(curl, CURLOPT_CAINFO, caChars);
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
            curl_easy_setopt(curl, CURLOPT_BUFFERSIZE, (long) BODY_CHUNK_BYTES);
            curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, errorText);
            curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, on_header);
            curl_easy_setopt(curl, CURLOPT_HEADERDATA, &transfer);
            curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, on_body);
            curl_easy_setopt(curl, CURLOPT_WRITEDATA, &transfer);
            curl_easy_setopt(curl, CURLOPT_NOPROGRESS, 0L);
            curl_easy_setopt(curl, CURLOPT_XFERINFOFUNCTION, on_progress);
            curl_easy_setopt(curl, CURLOPT_XFERINFODATA, &transfer);
            curl_easy_setopt(curl, CURLOPT_SOCKOPTFUNCTION, on_socket);
            curl_easy_setopt(curl, CURLOPT_SOCKOPTDATA, &transfer);
            code = curl_easy_perform(curl);
            curl_easy_setopt(curl, CURLOPT_HTTPHEADER, NULL);
            curl_easy_setopt(curl, CURLOPT_RESOLVE, NULL);
            curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, NULL);
        }
    }

    if (encodingChars != NULL) (*env)->ReleaseStringUTFChars(env, acceptEncoding, encodingChars);
    if (caChars != NULL) (*env)->ReleaseStringUTFChars(env, caFile, caChars);
    if (urlChars != NULL) (*env)->ReleaseStringUTFChars(env, url, urlChars);
    if (targetChars != NULL) (*env)->ReleaseStringUTFChars(env, target, targetChars);
    curl_slist_free_all(resolveList);
    curl_slist_free_all(headerList);
    (*env)->DeleteLocalRef(env, transfer.chunk);

    if (code != CURLE_OK && errorText[0] != '\0' && !(*env)->ExceptionCheck(env)) {
        jclass errorClass = (*env)->GetObjectClass(env, callback);
        jmethodID onError = (*env)->GetMethodID(env, errorClass, "onError", "(Ljava/lang/String;)V");
        if (onError != NULL) {
            jstring message = (*env)->NewStringUTF(env, errorText);
            if (message != NULL) {
                (*env)->CallVoidMethod(env, callback, onError, message);
                (*env)->DeleteLocalRef(env, message);
            }
        }
        (*env)->DeleteLocalRef(env, errorClass);
    }
    return (jint) code;
}
