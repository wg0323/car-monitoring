package com.cm.protocol;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.cm.protocol.body.AuthenticationRequest;
import com.cm.protocol.body.BatchPositionReport;
import com.cm.protocol.body.GenericAck;
import com.cm.protocol.body.PositionAdditional;
import com.cm.protocol.body.PositionQueryAck;
import com.cm.protocol.body.PositionReport;
import com.cm.protocol.body.RegistrationRequest;

/**
 * 把新解码结果渲染成 P0 冻结快照的文本口径，用于逐字段差分。
 * 渲染口径刻意保留旧实现的展示细节（例如应答消息 ID 按有符号 16 位输出），
 * 便于把「展示差异」与「语义差异」分开判断。
 */
final class GoldenSnapshotRenderer {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int BITS = 32;

    private static final String EMPTY = "";

    private GoldenSnapshotRenderer() {
    }

    static String render(String caseId, Jt808Message message) {
        StringBuilder text = new StringBuilder();
        text.append("=== ").append(caseId).append(" ===\n");
        text.append("frameCount=1\n");
        String prefix = "[0] ";
        MessageHeader header = message.header();
        text.append(prefix).append("type=0x").append(hex4(header == null ? 0 : header.messageId())).append('\n');
        text.append(prefix).append("simNo=").append(message.simNo()).append('\n');
        text.append(prefix).append("serialNo=").append(header == null ? 0 : header.serialNo()).append('\n');
        text.append(prefix).append("bodySize=").append(header == null ? 0 : header.bodySize()).append('\n');
        text.append(prefix).append("bodyProperty=0x")
                .append(hex4(header == null ? 0 : header.bodyProperty())).append('\n');
        text.append(prefix).append("isPackage=").append(header != null && header.packaged()).append('\n');
        text.append(prefix).append("totalPackets=").append(header == null ? 0 : header.totalPackets()).append('\n');
        text.append(prefix).append("packetNo=").append(header == null ? 0 : header.packetNo()).append('\n');
        text.append(prefix).append("checksum=").append(upperHex(message.checksum())).append('\n');
        text.append(prefix).append("error=").append(message.errorMessage() == null ? EMPTY
                : message.errorMessage()).append('\n');
        text.append(prefix).append("frameHex=").append(message.frameHex() == null ? EMPTY
                : message.frameHex()).append('\n');
        text.append(prefix).append("contentsClass=").append(contentsClass(message)).append('\n');
        text.append(prefix).append("childPacket=").append(upperHex(message.childPacket())).append('\n');
        appendContent(text, prefix, message.body());
        return text.toString();
    }

    private static String contentsClass(Jt808Message message) {
        if (message.body() == null) {
            return "-";
        }
        return "JT_" + hex4(message.body().messageId());
    }

    private static void appendContent(StringBuilder text, String prefix, MessageBody body) {
        if (body instanceof PositionReport position) {
            appendPosition(text, prefix + "content.", position, true);
        } else if (body instanceof RegistrationRequest registration) {
            text.append(prefix).append("content.getCityId=").append(registration.cityId()).append('\n');
            text.append(prefix).append("content.getManufactureId=").append(registration.manufactureId()).append('\n');
            text.append(prefix).append("content.getPlateColor=").append(registration.plateColor()).append('\n');
            text.append(prefix).append("content.getPlateNo=").append(registration.plateNo()).append('\n');
            text.append(prefix).append("content.getProvinceId=").append(registration.provinceId()).append('\n');
            text.append(prefix).append("content.getTerminalId=").append(registration.terminalId()).append('\n');
            text.append(prefix).append("content.getTerminalModelNo=").append(registration.terminalModelNo())
                    .append('\n');
        } else if (body instanceof AuthenticationRequest authentication) {
            text.append(prefix).append("content.getRegisterNo=").append(authentication.registerNo()).append('\n');
        } else if (body instanceof GenericAck ack) {
            text.append(prefix).append("content.getResponseMessageId=")
                    .append((short) ack.responseMessageId()).append('\n');
            text.append(prefix).append("content.getResponseMessageSerialNo=").append(ack.responseSerialNo())
                    .append('\n');
            text.append(prefix).append("content.getResponseResult=").append(ack.result()).append('\n');
        } else if (body instanceof PositionQueryAck queryAck) {
            text.append(prefix).append("content.getResponseMessageSerialNo=").append(queryAck.responseSerialNo())
                    .append('\n');
        } else if (body instanceof BatchPositionReport batch) {
            text.append(prefix).append("content.getPositionPacketNum=").append(batch.positionCount()).append('\n');
            text.append(prefix).append("content.getReportType=").append(batch.reportType()).append('\n');
            List<String> itemPrefixes = itemPrefixes("content.getPositionReportList", batch.positions().size());
            text.append(prefix).append("content.getPositionReportList.size=").append(batch.positions().size())
                    .append('\n');
            for (int index = 0; index < batch.positions().size(); index++) {
                appendPosition(text, prefix + itemPrefixes.get(index) + ".", batch.positions().get(index), false);
            }
        }
    }

    private static void appendPosition(StringBuilder text, String prefix, PositionReport position,
            boolean withAdditionals) {
        text.append(prefix).append("getAlarmFlag=").append(position.alarmFlag()).append('\n');
        text.append(prefix).append("getAltitude=").append(position.altitude()).append('\n');
        text.append(prefix).append("getCourse=").append(position.course()).append('\n');
        text.append(prefix).append("getLatitude=").append(position.latitude()).append('\n');
        text.append(prefix).append("getLongitude=").append(position.longitude()).append('\n');
        text.append(prefix).append("getMileage=").append(position.mileage()).append('\n');
        text.append(prefix).append("getOil=").append(position.oil()).append('\n');
        text.append(prefix).append("getRecorderSpeed=").append(position.recorderSpeed()).append('\n');
        text.append(prefix).append("getSignal=").append(position.signal()).append('\n');
        text.append(prefix).append("getSpeed=").append(position.speed()).append('\n');
        text.append(prefix).append("getStatus=").append(position.status()).append('\n');
        text.append(prefix).append("getStrStatus=").append(binary(position.status())).append('\n');
        text.append(prefix).append("getStrWarn=").append(binary(position.alarmFlag())).append('\n');
        text.append(prefix).append("getTime=").append(TIME_FORMAT.format(position.deviceTime())).append('\n');
        text.append(prefix).append("getVoltage=").append(position.voltage()).append('\n');
        if (!withAdditionals) {
            return;
        }
        List<PositionAdditional> additionals = position.additionals();
        text.append(prefix).append("getAdditionals.size=").append(additionals.size()).append('\n');
        for (int index = 0; index < additionals.size(); index++) {
            PositionAdditional item = additionals.get(index);
            String itemPrefix = prefix + "getAdditionals[" + index + "].";
            text.append(itemPrefix).append("getAdditionalId=").append(item.id()).append('\n');
            text.append(itemPrefix).append("getAdditionalLength=").append(item.length()).append('\n');
            if (item.isMileage()) {
                text.append(itemPrefix).append("getMileage=").append(item.unsignedIntValue()).append('\n');
            } else if (item.id() == PositionAdditional.OIL && item.isUnsignedShortValue()) {
                text.append(itemPrefix).append("getOil=").append(item.unsignedShortValue()).append('\n');
            } else if (item.id() == PositionAdditional.RECORDER_SPEED && item.isUnsignedShortValue()) {
                text.append(itemPrefix).append("getRecorderSpeed=").append(item.unsignedShortValue()).append('\n');
            }
        }
    }

    private static List<String> itemPrefixes(String collection, int size) {
        List<String> prefixes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            prefixes.add(collection + "[" + index + "]");
        }
        return prefixes;
    }

    private static String binary(int value) {
        String raw = Integer.toBinaryString(value);
        return raw.length() >= BITS ? raw : "0".repeat(BITS - raw.length()) + raw;
    }

    private static String hex4(int value) {
        return String.format(Locale.ROOT, "%04X", value & 0xFFFF);
    }

    static String upperHex(byte[] data) {
        if (data == null) {
            return EMPTY;
        }
        StringBuilder hex = new StringBuilder(data.length * 2);
        for (byte value : data) {
            hex.append(String.format(Locale.ROOT, "%02X", value & 0xFF));
        }
        return hex.toString();
    }
}
