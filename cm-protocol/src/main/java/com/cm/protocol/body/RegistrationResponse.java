package com.cm.protocol.body;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import com.cm.protocol.MessageBody;
import com.cm.protocol.ProtocolBytes;

/**
 * 0x8100 终端注册应答。结果 0 成功时带鉴权码，其它结果不带。
 *
 * <p>鉴权码按 GBK 裸字节写入，没有长度前缀：由黄金报文 DL-8100-01 反推——
 * 消息体共 14 字节 = 应答的注册流水号 2 + 结果 1 + 鉴权码 11（"1234567890A"）。
 * 结果码只实现 0 / 2 / 4：1（车辆已注册）与 3（终端已注册）在旧实现里依赖进程内 map，
 * 重启即失效，不是可靠的注册态事实源，新实现不引入新的注册态。
 */
public final class RegistrationResponse implements MessageBody {

    public static final int MESSAGE_ID = 0x8100;

    /** 成功，带鉴权码。 */
    public static final int RESULT_SUCCESS = 0;

    /** 数据库中无该车辆。 */
    public static final int RESULT_VEHICLE_NOT_FOUND = 2;

    /** 数据库中无该终端。 */
    public static final int RESULT_TERMINAL_NOT_FOUND = 4;

    private static final int FIXED_FIELDS_LENGTH = 3;

    private static final Charset GBK = Charset.forName("GBK");

    private final int registerSerialNo;

    private final int result;

    private final String authCode;

    private RegistrationResponse(int registerSerialNo, int result, String authCode) {
        this.registerSerialNo = registerSerialNo;
        this.result = result;
        this.authCode = authCode == null ? "" : authCode;
    }

    public static RegistrationResponse of(int registerSerialNo, int result, String authCode) {
        return new RegistrationResponse(registerSerialNo, result, authCode);
    }

    public static RegistrationResponse parse(byte[] body, int offset, int length) {
        ProtocolBytes.checkRange(body, offset, length);
        if (length < FIXED_FIELDS_LENGTH) {
            throw new IllegalArgumentException("registration response body is shorter than " + FIXED_FIELDS_LENGTH);
        }
        int serialNo = ProtocolBytes.unsignedShort(body, offset);
        int result = ProtocolBytes.unsignedByte(body, offset + 2);
        int authLength = length - FIXED_FIELDS_LENGTH;
        String authCode = authLength > 0
                ? ProtocolBytes.gbkString(body, offset + FIXED_FIELDS_LENGTH, authLength)
                : "";
        return new RegistrationResponse(serialNo, result, authCode);
    }

    /**
     * 只有成功结果才追加鉴权码，与旧系统 WriteToBytes 的口径一致。
     */
    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtocolBytes.writeShort(out, registerSerialNo);
        ProtocolBytes.writeByte(out, result);
        if (result == RESULT_SUCCESS) {
            byte[] code = authCode.getBytes(GBK);
            out.write(code, 0, code.length);
        }
        return out.toByteArray();
    }

    @Override
    public int messageId() {
        return MESSAGE_ID;
    }

    public int registerSerialNo() {
        return registerSerialNo;
    }

    public int result() {
        return result;
    }

    public String authCode() {
        return authCode;
    }
}
