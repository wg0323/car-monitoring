package com.cm.protocol.jt809;

/**
 * JT809 帧常量与消息 ID。
 * 帧结构取自旧实现 Tools.getHeaderAndFlag：长度字段等于全帧字节数（含 0x5B/0x5D），
 * 固定开销 26 字节，与 legacy-system-test-report-l3 的 4 条实测帧一致。
 */
public final class Jt809Constants {

    public static final int HEAD_FLAG = 0x5B;

    public static final int TAIL_FLAG = 0x5D;

    /** 5B 之后的固定头：长度4 + 流水号4 + 消息ID2 + 接入码4 + 版本3 + 加密标志1 + 密钥4。 */
    public static final int HEADER_LENGTH = 22;

    /** 5B + 头 + 校验2 + 5D；长度字段 = 消息体长度 + 该常量。 */
    public static final int FIXED_OVERHEAD = 26;

    public static final int CRC_LENGTH = 2;

    public static final int MIN_FRAME_LENGTH = FIXED_OVERHEAD;

    public static final int MAX_FRAME_LENGTH = 8 * 1024;

    public static final int MAX_BODY_LENGTH = MAX_FRAME_LENGTH - FIXED_OVERHEAD;

    public static final int VERSION_LENGTH = 3;

    public static final int UNENCRYPTED = 0;

    public static final long NO_ENCRYPT_KEY = 0L;

    /**
     * 旧实现 protocolVer="1.0" 经字符解析后落在帧里的实际版本字节，
     * 日志里表现为 0x1002/0x9001 的 "0001E0"。默认复刻该值保证与旧上级逐字节一致。
     */
    public static final byte[] LEGACY_VERSION = {0x00, 0x01, (byte) 0xE0};

    public static final int MSG_MAIN_LINK_LOGIN = 0x1001;

    public static final int MSG_MAIN_LINK_LOGIN_ACK = 0x1002;

    public static final int MSG_MAIN_LINK_LOGOUT = 0x1003;

    public static final int MSG_MAIN_LINK_LOGOUT_ACK = 0x1004;

    public static final int MSG_MAIN_LINK_TEST = 0x1005;

    public static final int MSG_MAIN_LINK_TEST_ACK = 0x1006;

    public static final int MSG_SUB_LINK_LOGIN = 0x9001;

    public static final int MSG_SUB_LINK_LOGIN_ACK = 0x9002;

    public static final int MSG_SUB_LINK_LOGOUT = 0x9003;

    public static final int MSG_SUB_LINK_LOGOUT_ACK = 0x9004;

    public static final int MSG_SUB_LINK_TEST = 0x9005;

    public static final int MSG_SUB_LINK_TEST_ACK = 0x9006;

    public static final int MSG_VEHICLE_LOCATION = 0x1200;

    public static final int MSG_WARN_URGE_TODO = 0x1400;

    /** 上级通过从链路下发的平台查岗请求。 */
    public static final int MSG_PLATFORM_POST_QUERY = 0x9301;

    /** 上级通过从链路下发的平台间报文请求。 */
    public static final int MSG_PLATFORM_MSG_INFO = 0x9302;

    public static final int SUB_TYPE_REAL_LOCATION = 0x1202;

    public static final int SUB_TYPE_HISTORY_LOCATION = 0x1203;

    public static final int SUB_TYPE_URGE_TODO_REQUEST = 0x1400;

    public static final int SUB_TYPE_URGE_TODO_ACK = 0x1401;

    public static final int PLATE_LENGTH = 21;

    public static final int LOCATION_SAMPLE_LENGTH = 36;

    public static final int URGE_TODO_ACK_DATA_LENGTH = 5;

    public static final int URGE_TODO_REQUEST_DATA_LENGTH = 90;

    /**
     * 消息 ID 的中文描述，取自旧 T809Constants 的文案口径；未登记返回空串。
     */
    public static String describe(int messageId) {
        return switch (messageId) {
            case MSG_MAIN_LINK_LOGIN -> "主链路登录请求";
            case MSG_MAIN_LINK_LOGIN_ACK -> "主链路登录应答";
            case MSG_MAIN_LINK_LOGOUT -> "主链路注销请求";
            case MSG_MAIN_LINK_LOGOUT_ACK -> "主链路注销应答";
            case MSG_MAIN_LINK_TEST -> "主链路心跳";
            case MSG_MAIN_LINK_TEST_ACK -> "主链路心跳应答";
            case MSG_SUB_LINK_LOGIN -> "从链路连接请求";
            case MSG_SUB_LINK_LOGIN_ACK -> "从链路连接应答";
            case MSG_SUB_LINK_LOGOUT -> "从链路断开请求";
            case MSG_SUB_LINK_LOGOUT_ACK -> "从链路断开应答";
            case MSG_VEHICLE_LOCATION -> "车辆定位信息";
            case MSG_WARN_URGE_TODO -> "报警督办请求";
            case MSG_PLATFORM_POST_QUERY -> "平台查岗请求";
            case MSG_PLATFORM_MSG_INFO -> "平台间报文请求";
            default -> "";
        };
    }

    private Jt809Constants() {
    }
}

