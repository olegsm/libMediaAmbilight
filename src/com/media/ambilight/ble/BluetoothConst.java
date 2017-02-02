package com.media.ambilight.ble;

public class BluetoothConst {

    //https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-4.4.4_r2.0.1/stack/include/gatt_api.h

    public static final int GATT_SUCCESS                        = 0x0000;
    public static final int GATT_INVALID_HANDLE                 = 0x0001;
    public static final int GATT_READ_NOT_PERMIT                = 0x0002;
    public static final int GATT_WRITE_NOT_PERMIT               = 0x0003;
    public static final int GATT_INVALID_PDU                    = 0x0004;
    public static final int GATT_INSUF_AUTHENTICATION           = 0x0005;
    public static final int GATT_REQ_NOT_SUPPORTED              = 0x0006;
    public static final int GATT_INVALID_OFFSET                 = 0x0007;
    public static final int GATT_INSUF_AUTHORIZATION            = 0x0008;
    public static final int GATT_PREPARE_Q_FULL                 = 0x0009;
    public static final int GATT_NOT_FOUND                      = 0x000a;
    public static final int GATT_NOT_LONG                       = 0x000b;
    public static final int GATT_INSUF_KEY_SIZE                 = 0x000c;
    public static final int GATT_INVALID_ATTR_LEN               = 0x000d;
    public static final int GATT_ERR_UNLIKELY                   = 0x000e;
    public static final int GATT_INSUF_ENCRYPTION               = 0x000f;
    public static final int GATT_UNSUPPORT_GRP_TYPE             = 0x0010;
    public static final int GATT_INSUF_RESOURCE                 = 0x0011;
    public static final int GATT_ILLEGAL_PARAMETER              = 0x0087;
    public static final int GATT_NO_RESOURCES                   = 0x0080;
    public static final int GATT_INTERNAL_ERROR                 = 0x0081;
    public static final int GATT_WRONG_STATE                    = 0x0082;
    public static final int GATT_DB_FULL                        = 0x0083;
    public static final int GATT_BUSY                           = 0x0084;
    public static final int GATT_ERROR                          = 0x0085;
    public static final int GATT_CMD_STARTED                    = 0x0086;
    public static final int GATT_PENDING                        = 0x0088;
    public static final int GATT_AUTH_FAIL                      = 0x0089;
    public static final int GATT_MORE                           = 0x008a;
    public static final int GATT_INVALID_CFG                    = 0x008b;
    public static final int GATT_SERVICE_STARTED                = 0x008c;
    public static final int GATT_ENCRYPED_MITM                  = GATT_SUCCESS;
    public static final int GATT_ENCRYPED_NO_MITM               = 0x008d;
    public static final int GATT_NOT_ENCRYPTED                  = 0x008e;
}
