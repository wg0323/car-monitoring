package com.cm.integration.forward;

import com.cm.infrastructure.gov809.Gov809OutboxService;
import com.cm.infrastructure.persistence.entity.CmGov809Outbox;
import com.cm.integration.config.Gov809Properties;
import com.cm.integration.link.Gov809LinkManager;
import com.cm.integration.link.Gov809LinkUpEvent;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.body.LocationSample;
import com.cm.protocol.jt809.body.VehicleLocation;
import com.cm.protocol.jt809.body.VehicleLocationBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 重连补发。定位按事件序号排序，同车牌连续多条合并为一帧 0x1203；
 * 其余报文（督办应答等）逐条原样重发。一条写失败就停：链路又断了，
 * 剩下的继续留着，下次登录成功再补，不在这重试里空转。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Gov809ReplayService {

    private static final int CLAIM_BATCH = 200;

    private static final HexFormat HEX = HexFormat.of();

    private final Gov809OutboxService outboxService;

    private final Gov809LinkManager linkManager;

    private final Gov809Properties properties;

    @EventListener
    public void onLinkUp(Gov809LinkUpEvent event) {
        replay(event.platformId());
    }

    public synchronized void replay(long platformId) {
        if (!linkManager.session().hasUsableLink()) {
            return;
        }
        try {
            doReplay(platformId);
        } catch (RuntimeException ex) {
            // 补发失败不影响链路本身：异常只终止本轮，剩下的留到下次登录再补。
            log.warn("809 补发异常，本轮中止, platformId={}", platformId, ex);
        }
    }

    private void doReplay(long platformId) {
        int sentRows = 0;
        while (sentRows < properties.getReplayMaxRows()) {
            List<CmGov809Outbox> rows = outboxService.claimPending(platformId, CLAIM_BATCH);
            if (rows.isEmpty()) {
                return;
            }
            if (!replayBatch(rows)) {
                return;
            }
            sentRows += rows.size();
        }
        log.warn("809 补发达到单次上限，剩余积压留到下次链路恢复, platformId={}, replayMaxRows={}",
                platformId, properties.getReplayMaxRows());
    }

    private boolean replayBatch(List<CmGov809Outbox> rows) {
        int index = 0;
        while (index < rows.size()) {
            CmGov809Outbox row = rows.get(index);
            if (isPosition(row)) {
                int grouped = replayPositions(rows, index);
                if (grouped < 0) {
                    // 该行不可解析，已记失败次数，跳过继续，不让一行坏数据堵住整个队列。
                    index++;
                    continue;
                }
                if (grouped == 0) {
                    return false;
                }
                index += grouped;
            } else {
                byte[] body;
                try {
                    body = HEX.parseHex(row.getPayload());
                } catch (IllegalArgumentException ex) {
                    outboxService.markFailed(row.getId(), "payload 非法: " + ex.getMessage());
                    index++;
                    continue;
                }
                if (!linkManager.send(row.getMsgType(), body)) {
                    log.warn("809 补发失败，暂停本轮补发, eventId={}", row.getBizEventId());
                    return false;
                }
                outboxService.markSent(List.of(row.getId()));
                index++;
            }
        }
        return true;
    }

    /**
     * 同车牌的连续定位合并为一帧 0x1203，单条仍按原来的 0x1202 发；
     * 返回已发出的条数，0 表示写入失败，调用方停止本轮补发。
     */
    private int replayPositions(List<CmGov809Outbox> rows, int start) {
        CmGov809Outbox first = rows.get(start);
        List<Long> ids = new ArrayList<>();
        List<LocationSample> samples = new ArrayList<>();
        int index = start;
        while (index < rows.size() && ids.size() < properties.getReplayBatchSize()) {
            CmGov809Outbox candidate = rows.get(index);
            if (!isPosition(candidate) || !first.getPlateNo().equals(candidate.getPlateNo())) {
                break;
            }
            try {
                samples.add(VehicleLocation.parse(HEX.parseHex(candidate.getPayload())).sample());
            } catch (IllegalArgumentException ex) {
                // 单条坏数据只废掉它自己：已累积的正常条目照发，本条计数失败跳过。
                outboxService.markFailed(candidate.getId(), "定位 payload 非法: " + ex.getMessage());
                if (ids.isEmpty()) {
                    return -1;
                }
                index++;
                break;
            }
            ids.add(candidate.getId());
            index++;
        }
        byte[] body = samples.size() == 1
                ? HEX.parseHex(first.getPayload())
                : new VehicleLocationBatch(first.getPlateNo(), first.getPlateColor(), samples).encode();
        if (!linkManager.send(Jt809Constants.MSG_VEHICLE_LOCATION, body)) {
            log.warn("809 定位补发失败，暂停本轮补发, plateNo={}, count={}",
                    first.getPlateNo(), ids.size());
            return 0;
        }
        outboxService.markSent(ids);
        log.debug("809 定位补发完成, plateNo={}, count={}, batch={}", first.getPlateNo(),
                ids.size(), samples.size() > 1);
        return ids.size();
    }

    private boolean isPosition(CmGov809Outbox row) {
        return row.getMsgType() != null && row.getMsgType() == Jt809Constants.MSG_VEHICLE_LOCATION
                && row.getSubType() != null
                && row.getSubType() == Jt809Constants.SUB_TYPE_REAL_LOCATION;
    }
}
 
