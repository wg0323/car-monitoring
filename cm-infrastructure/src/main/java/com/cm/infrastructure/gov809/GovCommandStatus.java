package com.cm.infrastructure.gov809;

/**
 * 809 指令日志状态值。旧系统存英文枚举再由 BasicData 字典翻译，
 * 新系统没有迁移该字典，直接存页面可展示的中文，避免多一层码表依赖。
 * <p>改文案时必须同步修改 {@code Jt809CommandMapper.xml}：轮询、抢占与回写三处 SQL 仍以字面量引用这些值，
 * 只改常量不会报编译错，但命令会静默地抢不到或轮不到。
 */
public final class GovCommandStatus {

    /** 管理端已受理，等待企业侧链路进程执行。 */
    public static final String PENDING = "待下发";

    /** 已被执行器抢占，正在发送。 */
    public static final String RUNNING = "执行中";

    /** 帧已写入链路。 */
    public static final String SENT = "发送成功";

    /** 链路不可用或写入失败。 */
    public static final String FAILED = "发送失败";

    /** 收到上级下发报文。 */
    public static final String RECEIVED = "接收成功";

    /** 重复收到同一业务报文。 */
    public static final String DUPLICATE = "重复接收";

    /** 登录被拒等确定性失败。 */
    public static final String REJECTED = "失败";

    private GovCommandStatus() {
    }
}
