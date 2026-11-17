package com.cm.gateway.telemetry;

import com.cm.domain.position.PositionTimePolicy;
import com.cm.domain.position.PositionTimeVerdict;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.PositionSample;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import com.cm.protocol.body.PositionReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;

/**
 * 把协议层位置对象组装成定位事件。eventId 完全由原始帧决定：同一帧重传得到同一个 ID，
 * 消费端据此去重；批量定位每条额外带 batchIndex，避免一帧内多个业务点共用一个 ID。
 * payloadDigest 取整帧摘要，用来区分流水号与时间相同但内容不同的冲突报文。
 */
@Component
@RequiredArgsConstructor
public class PositionEventAssembler {

    private static final String PROTOCOL_STANDARD = "jt808-2011";

    private static final String PROTOCOL_BATCH = "jt808-batch";

    private static final String PROTOCOL_PRIVATE = "jt808-private";

    private static final String EVENT_ID_FORMAT = "%s:%s:%04d:%d:%d:%s:%03d";

    private static final int DIGEST_HEX_LENGTH = 16;

    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final PositionTimePolicy timePolicy;

    private final TelemetryProperties telemetryProperties;

    private final CmCommandProperties commandProperties;

    public PositionEvent assemble(String simNo, int messageId, int serialNo, PositionReport report,
            String branch, int batchIndex, byte[] frame, Instant ingestTime) {
        ZoneId zone = ZoneId.of(telemetryProperties.getTime().getZone());
        Instant deviceInstant = report.deviceTime().atZone(zone).toInstant();
        PositionTimeVerdict verdict = timePolicy.evaluate(report.deviceTime(), ingestTime);
        String digest = digest(frame);
        String eventId = String.format(EVENT_ID_FORMAT, protocolVersion(branch), simNo, messageId,
                serialNo, deviceInstant.getEpochSecond(), digest, batchIndex);
        PositionSample sample = new PositionSample(report.alarmFlag(), report.status(), report.latitude(),
                report.longitude(), report.altitude(), report.speed(), report.course(), report.mileage(),
                report.oil(), report.recorderSpeed(), report.signal(), report.voltage());
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, eventId, PositionEvent.EVENT_TYPE, branch,
                simNo, serialNo, deviceInstant.toString(), ingestTime.toString(),
                verdict.effectiveDeviceTime().toString(), verdict.quality().name(),
                commandProperties.getGatewayId(), eventId, batchIndex, sample, digest);
    }

    private String protocolVersion(String branch) {
        return switch (branch) {
            case PositionEvent.BRANCH_BATCH -> PROTOCOL_BATCH;
            case PositionEvent.BRANCH_PRIVATE -> PROTOCOL_PRIVATE;
            default -> PROTOCOL_STANDARD;
        };
    }

    private String digest(byte[] frame) {
        try {
            byte[] hash = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(frame);
            return HexFormat.of().formatHex(hash).substring(0, DIGEST_HEX_LENGTH);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
