
#include <termios.h>
#include <android/log.h>
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>

static const  char* TAG = "serial_port";

#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)


/**
 *
 * @param baudrate
 * @return
 */
static speed_t  getBaudrate(jint baudrate){
    switch (baudrate) {
        case 0:
            return B0;
        case 50:
            return B50;
        case 75:
            return B75;
        case 110:
            return B110;
        case 134:
            return B134;
        case 150:
            return B150;
        case 200:
            return B200;
        case 300:
            return B300;
        case 600:
            return B600;
        case 1200:
            return B1200;
        case 1800:
            return B1800;
        case 2400:
            return B2400;
        case 4800:
            return B4800;
        case 9600:
            return B9600;
        case 19200:
            return B19200;
        case 38400:
            return B38400;
        case 57600:
            return B57600;
        case 115200:
            return B115200;
        case 230400:
            return B230400;
        case 460800:
            return B460800;
        case 500000:
            return B500000;
        case 576000:
            return B576000;
        case 921600:
            return B921600;
        case 1000000:
            return B1000000;
        case 1152000:
            return B1152000;
        case 1500000:
            return B1500000;
        case 2000000:
            return B2000000;
        case 2500000:
            return B2500000;
        case 3000000:
            return B3000000;
        case 3500000:
            return B3500000;
        case 4000000:
            return B4000000;
        default:
            return -1;
    }
}



extern "C" JNIEXPORT jobject
JNICALL
Java_com_taisau_android_common_serialport_SerialPortImpl_openNative(
        JNIEnv *env,
        jobject thiz,
        jstring absolute_path,
        jint baudrate,
        jint data_bits,
        jint parity,
        jint stop_bits,
        jint flags) {
    speed_t speed = getBaudrate(baudrate);
    if(speed == - 1){
//        LOGE("valid ");
        return nullptr;
    }

    /*打开设备串口*/
    jboolean isCopy;
    const char *path_utf = nullptr;
    path_utf = env ->GetStringUTFChars(absolute_path, &isCopy);
    int fd = open(path_utf,O_RDWR | flags);
    env -> ReleaseStringUTFChars(absolute_path, path_utf);
    if (fd == -1){
        return nullptr;
    }

    /**/
    struct termios cfg{};
    if (tcgetattr(fd,&cfg)){
        return nullptr;
    }
    cfmakeraw(&cfg);
    cfsetispeed(&cfg,speed);
    cfsetospeed(&cfg,speed);

    cfg.c_cflag &= ~CSIZE;
    switch (data_bits) {
        case 5:
            cfg.c_cflag |= CS5;  // 使用5位数据位
            break;
        case 6:
            cfg.c_cflag |= CS6;
            break;
        case 7:
            cfg.c_cflag |= CS7;
            break;
        default:
            cfg.c_cflag |= CS8;
            break;
    }

    switch (parity) {
        case 1:
            cfg.c_cflag |= (PARODD | PARENB);  //
            break;
        case 2:
            cfg.c_iflag &= ~(IGNPAR | PARMRK); // 偶校验
            cfg.c_iflag |= INPCK;
            cfg.c_cflag |= PARENB;
            cfg.c_cflag &= ~PARODD;
            break;
        default:
            cfg.c_cflag &= ~PARENB;  //
            break;

    }
    if (stop_bits == 2){
        cfg.c_cflag |= CSTOPB;    //2位停止位
    } else{
        cfg.c_cflag &= ~CSTOPB;    //1位停止位
    }

    if (tcsetattr(fd,TCSANOW,&cfg)){  // 判断配置成功
        close(fd);
        return NULL;
    }

    jclass cFileDescriptor =  env->FindClass("java/io/FileDescriptor");
    jmethodID iFileDescriptor = env->GetMethodID(cFileDescriptor,"<init>", "()V");
    jobject mFileDescriptor = env->NewObject(cFileDescriptor,iFileDescriptor);

    jfieldID fdField = env->GetFieldID(cFileDescriptor, "fd", "I");
    if (fdField != nullptr) {
        env->SetIntField(mFileDescriptor, fdField, fd);
    }

    return mFileDescriptor;
}

extern "C" JNIEXPORT void
        JNICALL
Java_com_taisau_android_common_serialport_SerialPortImpl_closeNative(
        JNIEnv *env,
jobject thiz) {
jclass SerialPortImplClass = env->GetObjectClass(thiz);
jclass FileDescriptorClass = env->FindClass("java/io/FileDescriptor");

jfieldID fdID = env->GetFieldID(SerialPortImplClass, "fd", "Ljava/io/FileDescriptor;");
jfieldID descriptorID = env->GetFieldID(FileDescriptorClass, "fd", "I");

jobject fdObject = env->GetObjectField(thiz, fdID);
if (fdObject == nullptr) {
    return;
}

jint descriptor = env->GetIntField(fdObject, descriptorID);
if (descriptor > 0) {
    close(descriptor);
}

}
