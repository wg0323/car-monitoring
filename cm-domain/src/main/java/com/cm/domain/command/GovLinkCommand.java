package com.cm.domain.command;

/**
 * 809 链路命令。旧系统监管平台命令页只下发这四条，链接命令直接作为帧消息 ID，
 * 不带子类型（与 0x1200 车辆定位交换是两类报文）。
 */
public final class GovLinkCommand {

    /** 连接主链路：主链路发送 0x1001 登录申请。 */
    public static final int CONNECT_MAIN_LINK = 0x1001;

    /** 主链路注销请求：主链路发送 0x1003。 */
    public static final int LOGOUT_MAIN_LINK = 0x1003;

    /** 主链路断开通知：从链路发送 0x1007。 */
    public static final int NOTIFY_MAIN_LINK_DISCONNECTED = 0x1007;

    /** 主动关闭主从链路通知：从链路发送 0x1008。 */
    public static final int NOTIFY_CLOSE_LINK = 0x1008;

    private GovLinkCommand() {
    }

    public static boolean isSupported(int linkCmd) {
        return linkCmd == CONNECT_MAIN_LINK
                || linkCmd == LOGOUT_MAIN_LINK
                || linkCmd == NOTIFY_MAIN_LINK_DISCONNECTED
                || linkCmd == NOTIFY_CLOSE_LINK;
    }

    /**
     * 命令类型描述，与旧系统 T809Constants 的文案口径一致。
     */
    public static String describe(int linkCmd) {
        return switch (linkCmd) {
            case CONNECT_MAIN_LINK -> "[0x1001]主链路登录请求";
            case LOGOUT_MAIN_LINK -> "[0x1003]主链路注销请求";
            case NOTIFY_MAIN_LINK_DISCONNECTED -> "[0x1007]主链路断开通知";
            case NOTIFY_CLOSE_LINK -> "[0x1008]主动关闭主从链路通知";
            default -> "[0x" + Integer.toHexString(linkCmd) + "]未知链路命令";
        };
    }
}
