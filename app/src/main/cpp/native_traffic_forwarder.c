#include <jni.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <sys/select.h>
#include <unistd.h>

#include "zdtun.h"

static JavaVM *java_vm;
static jobject vpn_service;
static jmethodID protect_method;
static jmethodID classify_connection_method;
static jmethodID register_proxy_connection_method;
static jmethodID release_connection_method;
static zdtun_t *tunnel;
static int tun_fd = -1;
static pthread_t event_thread;
static pthread_mutex_t tunnel_mutex = PTHREAD_MUTEX_INITIALIZER;
static volatile bool running;
static bool thread_started;
static volatile bool protection_failed;

enum connection_disposition {
    CONNECTION_DIRECT = 0,
    CONNECTION_INSPECT = 1,
};

static int send_to_client(zdtun_t *unused, zdtun_pkt_t *packet, const zdtun_conn_t *connection) {
    (void) unused;
    (void) connection;
    ssize_t written = write(tun_fd, packet->buf, packet->len);
    return written == packet->len ? 0 : -1;
}

static void protect_socket(zdtun_t *unused, socket_t socket_fd) {
    (void) unused;
    JNIEnv *env = NULL;
    bool attached = false;
    if ((*java_vm)->GetEnv(java_vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*java_vm)->AttachCurrentThread(java_vm, &env, NULL) != JNI_OK) return;
        attached = true;
    }
    jboolean protected = (*env)->CallBooleanMethod(env, vpn_service, protect_method, (jint) socket_fd);
    if (!protected) protection_failed = true;
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
}

static int classify_connection(zdtun_t *unused, zdtun_conn_t *connection) {
    (void) unused;
    const zdtun_5tuple_t *tuple = zdtun_conn_get_5tuple(connection);
    JNIEnv *env = NULL;
    bool attached = false;
    int disposition = CONNECTION_DIRECT;
    if ((*java_vm)->GetEnv(java_vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*java_vm)->AttachCurrentThread(java_vm, &env, NULL) != JNI_OK) return 0;
        attached = true;
    }

    int address_length = tuple->ipver == 4 ? 4 : 16;
    const void *source_address = tuple->ipver == 4
        ? (const void *) &tuple->src_ip.ip4
        : (const void *) &tuple->src_ip.ip6;
    const void *destination_address = tuple->ipver == 4
        ? (const void *) &tuple->dst_ip.ip4
        : (const void *) &tuple->dst_ip.ip6;
    jbyteArray source_bytes = (*env)->NewByteArray(env, address_length);
    jbyteArray destination_bytes = (*env)->NewByteArray(env, address_length);
    if (source_bytes != NULL && destination_bytes != NULL) {
        (*env)->SetByteArrayRegion(env, source_bytes, 0, address_length, source_address);
        (*env)->SetByteArrayRegion(env, destination_bytes, 0, address_length, destination_address);
        disposition = (*env)->CallIntMethod(
            env,
            vpn_service,
            classify_connection_method,
            (jint) tuple->ipver,
            (jint) tuple->ipproto,
            source_bytes,
            (jint) ntohs(tuple->src_port),
            destination_bytes,
            (jint) ntohs(tuple->dst_port)
        );
    }
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        disposition = CONNECTION_DIRECT;
    }
    if (disposition > CONNECTION_DIRECT) zdtun_conn_proxy(connection);
    if (source_bytes != NULL) (*env)->DeleteLocalRef(env, source_bytes);
    if (destination_bytes != NULL) (*env)->DeleteLocalRef(env, destination_bytes);
    zdtun_conn_set_userdata(connection, (void *) (intptr_t) disposition);
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
    return 0;
}

static void socket_connected(
    zdtun_t *unused,
    socket_t socket_fd,
    const zdtun_conn_t *connection
) {
    (void) unused;
    intptr_t connection_id = (intptr_t) zdtun_conn_get_userdata(connection);
    if (connection_id <= CONNECTION_DIRECT) return;
    struct sockaddr_storage address = {0};
    socklen_t address_length = sizeof(address);
    if (getsockname(socket_fd, (struct sockaddr *) &address, &address_length) != 0) return;
    uint16_t source_port = address.ss_family == AF_INET
        ? ntohs(((struct sockaddr_in *) &address)->sin_port)
        : ntohs(((struct sockaddr_in6 *) &address)->sin6_port);

    JNIEnv *env = NULL;
    bool attached = false;
    if ((*java_vm)->GetEnv(java_vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*java_vm)->AttachCurrentThread(java_vm, &env, NULL) != JNI_OK) return;
        attached = true;
    }
    (*env)->CallVoidMethod(
        env,
        vpn_service,
        register_proxy_connection_method,
        (jint) connection_id,
        (jint) source_port
    );
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
}

static void connection_closed(zdtun_t *unused, const zdtun_conn_t *connection) {
    (void) unused;
    intptr_t connection_id = (intptr_t) zdtun_conn_get_userdata(connection);
    if (connection_id <= CONNECTION_DIRECT) return;
    JNIEnv *env = NULL;
    bool attached = false;
    if ((*java_vm)->GetEnv(java_vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*java_vm)->AttachCurrentThread(java_vm, &env, NULL) != JNI_OK) return;
        attached = true;
    }
    (*env)->CallVoidMethod(env, vpn_service, release_connection_method, (jint) connection_id);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
}

static void *run_event_loop(void *unused) {
    (void) unused;
    while (running) {
        fd_set read_fds;
        fd_set write_fds;
        int max_fd = 0;
        struct timeval timeout = {.tv_sec = 0, .tv_usec = 100000};

        FD_ZERO(&read_fds);
        FD_ZERO(&write_fds);
        pthread_mutex_lock(&tunnel_mutex);
        if (tunnel != NULL) zdtun_fds(tunnel, &max_fd, &read_fds, &write_fds);
        pthread_mutex_unlock(&tunnel_mutex);

        int selected = select(max_fd + 1, &read_fds, &write_fds, NULL, &timeout);
        if (selected <= 0 || !running) continue;
        pthread_mutex_lock(&tunnel_mutex);
        if (tunnel != NULL) zdtun_handle_fd(tunnel, &read_fds, &write_fds);
        pthread_mutex_unlock(&tunnel_mutex);
    }
    return NULL;
}

JNIEXPORT jboolean JNICALL
Java_com_haoze_dnssr_vpn_NativeTrafficForwarder_nativeStart(
    JNIEnv *env,
    jobject instance,
    jint input_tun_fd,
    jobject service,
    jint local_proxy_port
) {
    (void) instance;
    pthread_mutex_lock(&tunnel_mutex);
    if (tunnel != NULL) {
        pthread_mutex_unlock(&tunnel_mutex);
        return JNI_TRUE;
    }

    tun_fd = dup(input_tun_fd);
    if (tun_fd < 0) {
        pthread_mutex_unlock(&tunnel_mutex);
        return JNI_FALSE;
    }
    vpn_service = (*env)->NewGlobalRef(env, service);
    jclass service_class = (*env)->GetObjectClass(env, service);
    protect_method = (*env)->GetMethodID(env, service_class, "protect", "(I)Z");
    classify_connection_method = (*env)->GetMethodID(
        env,
        service_class,
        "classifyNativeConnection",
        "(II[BI[BI)I"
    );
    register_proxy_connection_method = (*env)->GetMethodID(
        env, service_class, "registerNativeProxyConnection", "(II)V"
    );
    release_connection_method = (*env)->GetMethodID(
        env, service_class, "releaseNativeConnection", "(I)V"
    );
    (*env)->DeleteLocalRef(env, service_class);
    if (vpn_service == NULL || protect_method == NULL || classify_connection_method == NULL ||
        register_proxy_connection_method == NULL || release_connection_method == NULL
    ) goto failure;

    zdtun_callbacks_t callbacks = {
        .send_client = send_to_client,
        .on_socket_open = protect_socket,
        .on_socket_connected = socket_connected,
        .on_connection_open = classify_connection,
        .on_connection_close = connection_closed,
    };
    tunnel = zdtun_init(&callbacks, NULL);
    if (tunnel == NULL) goto failure;
    zdtun_ip_t proxy_address = {0};
    if (local_proxy_port <= 0 || local_proxy_port > 65535 ||
        inet_pton(AF_INET, "127.0.0.1", &proxy_address.ip4) != 1
    ) goto failure;
    zdtun_set_socks5_proxy(
        tunnel,
        &proxy_address,
        htons((uint16_t) local_proxy_port),
        4
    );

    signal(SIGPIPE, SIG_IGN);
    running = true;
    protection_failed = false;
    if (pthread_create(&event_thread, NULL, run_event_loop, NULL) != 0) {
        running = false;
        zdtun_finalize(tunnel);
        tunnel = NULL;
        goto failure;
    }
    thread_started = true;
    pthread_mutex_unlock(&tunnel_mutex);
    return JNI_TRUE;

failure:
    if (tunnel != NULL) {
        zdtun_finalize(tunnel);
        tunnel = NULL;
    }
    if (vpn_service != NULL) {
        (*env)->DeleteGlobalRef(env, vpn_service);
        vpn_service = NULL;
    }
    if (tun_fd >= 0) close(tun_fd);
    tun_fd = -1;
    pthread_mutex_unlock(&tunnel_mutex);
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_haoze_dnssr_vpn_NativeTrafficForwarder_nativeForward(
    JNIEnv *env,
    jobject instance,
    jbyteArray packet,
    jint length
) {
    (void) instance;
    jsize array_length = (*env)->GetArrayLength(env, packet);
    if (length <= 0 || length > array_length) return 0;
    jbyte *bytes = (*env)->GetByteArrayElements(env, packet, NULL);
    if (bytes == NULL) return -1;

    pthread_mutex_lock(&tunnel_mutex);
    if (tunnel == NULL || protection_failed) {
        pthread_mutex_unlock(&tunnel_mutex);
        (*env)->ReleaseByteArrayElements(env, packet, bytes, JNI_ABORT);
        return -1;
    }
    zdtun_conn_t *connection = zdtun_easy_forward(tunnel, (const char *) bytes, length);
    int result = protection_failed ? -1 : (connection == NULL ? 0 : 1);
    pthread_mutex_unlock(&tunnel_mutex);
    (*env)->ReleaseByteArrayElements(env, packet, bytes, JNI_ABORT);
    return result;
}

JNIEXPORT void JNICALL
Java_com_haoze_dnssr_vpn_NativeTrafficForwarder_nativeStop(
    JNIEnv *env,
    jobject instance
) {
    (void) instance;
    running = false;
    if (thread_started) {
        pthread_join(event_thread, NULL);
        thread_started = false;
    }
    pthread_mutex_lock(&tunnel_mutex);
    if (tunnel != NULL) {
        zdtun_finalize(tunnel);
        tunnel = NULL;
    }
    if (tun_fd >= 0) close(tun_fd);
    tun_fd = -1;
    if (vpn_service != NULL) {
        (*env)->DeleteGlobalRef(env, vpn_service);
        vpn_service = NULL;
    }
    protect_method = NULL;
    classify_connection_method = NULL;
    register_proxy_connection_method = NULL;
    release_connection_method = NULL;
    pthread_mutex_unlock(&tunnel_mutex);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    java_vm = vm;
    return JNI_VERSION_1_6;
}
