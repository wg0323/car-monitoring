package com.cm.infrastructure.gov809;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cm.infrastructure.persistence.entity.CmGov809Outbox;
import com.cm.infrastructure.persistence.mapper.CmGov809OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 积压队列实现。单链路只有一个写入者（gov809.forward 单消费者），
 * 所以序号在内存里递增即可，重启时用库里的最大序号续号，不需要每写一行都抢锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Gov809OutboxServiceImpl implements Gov809OutboxService {

    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_SENT = "SENT";

    private static final String STATUS_FAILED = "FAILED";

    private static final String STATUS_DROPPED = "DROPPED";

    private static final int POSITION_MSG_TYPE = 0x1200;

    private static final int PAYLOAD_MAX_LENGTH = 512;

    private static final int ERROR_MAX_LENGTH = 255;

    private final CmGov809OutboxMapper outboxMapper;

    private final Gov809OutboxProperties properties;

    private final Map<Long, AtomicLong> sequences = new HashMap<>();

    @Override
    public synchronized boolean append(Gov809OutboxMessage message) {
        CmGov809Outbox row = new CmGov809Outbox();
        row.setPlatformId(message.platformId());
        row.setEventSeq(nextSeq(message.platformId()));
        row.setBizEventId(message.bizEventId());
        row.setMsgType(message.msgType());
        row.setSubType(message.subType());
        row.setPlateNo(message.plateNo() == null ? "" : message.plateNo());
        row.setPlateColor(message.plateColor());
        row.setPayload(truncate(message.payloadHex(), PAYLOAD_MAX_LENGTH));
        row.setOutboxStatus(STATUS_PENDING);
        row.setAttempt(0);
        row.setLastError("");
        try {
            outboxMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            log.debug("809 事件已入过积压队列，跳过, bizEventId={}", message.bizEventId());
            return false;
        }
        pruneIfOverflow(message.platformId());
        return true;
    }

    @Override
    public List<CmGov809Outbox> claimPending(long platformId, int limit) {
        return outboxMapper.selectList(Wrappers.<CmGov809Outbox>lambdaQuery()
                .eq(CmGov809Outbox::getPlatformId, platformId)
                .eq(CmGov809Outbox::getOutboxStatus, STATUS_PENDING)
                .lt(CmGov809Outbox::getAttempt, properties.getMaxAttempt())
                .orderByAsc(CmGov809Outbox::getEventSeq)
                .last("LIMIT " + Math.max(limit, 1)));
    }

    @Override
    public void markSent(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        outboxMapper.update(null, Wrappers.<CmGov809Outbox>lambdaUpdate()
                .in(CmGov809Outbox::getId, ids)
                .eq(CmGov809Outbox::getOutboxStatus, STATUS_PENDING)
                .set(CmGov809Outbox::getOutboxStatus, STATUS_SENT)
                .set(CmGov809Outbox::getSentAt, LocalDateTime.now()));
    }

    /**
     * 失败一律回退为 PENDING：链路断开时下一次重连还会再补；
     * 超过最大尝试次数才置 FAILED，避免一条坏报文把整条链路堵死。
     */
    @Override
    public void markFailed(long id, String error) {
        CmGov809Outbox row = outboxMapper.selectById(id);
        if (row == null) {
            return;
        }
        int attempt = (row.getAttempt() == null ? 0 : row.getAttempt()) + 1;
        String status = attempt >= properties.getMaxAttempt() ? STATUS_FAILED : STATUS_PENDING;
        outboxMapper.update(null, Wrappers.<CmGov809Outbox>lambdaUpdate()
                .eq(CmGov809Outbox::getId, id)
                .set(CmGov809Outbox::getAttempt, attempt)
                .set(CmGov809Outbox::getOutboxStatus, status)
                .set(CmGov809Outbox::getLastError, truncate(error, ERROR_MAX_LENGTH)));
    }

    @Override
    public long countPending(long platformId) {
        Long count = outboxMapper.selectCount(Wrappers.<CmGov809Outbox>lambdaQuery()
                .eq(CmGov809Outbox::getPlatformId, platformId)
                .eq(CmGov809Outbox::getOutboxStatus, STATUS_PENDING));
        return count == null ? 0L : count;
    }

    @Override
    public long maxAllocatedSeq(long platformId) {
        return maxSeq(platformId, null);
    }

    @Override
    public long maxSentSeq(long platformId) {
        return maxSeq(platformId, STATUS_SENT);
    }

    private long maxSeq(long platformId, String status) {
        QueryWrapper<CmGov809Outbox> query = Wrappers.<CmGov809Outbox>query()
                .select("IFNULL(MAX(event_seq), 0)")
                .eq("platform_id", platformId);
        if (status != null) {
            query.eq("outbox_status", status);
        }
        List<Object> values = outboxMapper.selectObjs(query);
        if (values.isEmpty() || !(values.get(0) instanceof Number number)) {
            return 0L;
        }
        return number.longValue();
    }

    private long nextSeq(long platformId) {
        return sequences.computeIfAbsent(platformId,
                key -> new AtomicLong(maxAllocatedSeq(key))).incrementAndGet();
    }

    /**
     * 容量裁剪只动定位报文：督办应答、链路事件必须补到，丢定位最多是轨迹缺一小段。
     */
    private void pruneIfOverflow(long platformId) {
        if (countPending(platformId) <= properties.getMaxPending()) {
            return;
        }
        List<CmGov809Outbox> oldest = outboxMapper.selectList(Wrappers.<CmGov809Outbox>lambdaQuery()
                .eq(CmGov809Outbox::getPlatformId, platformId)
                .eq(CmGov809Outbox::getOutboxStatus, STATUS_PENDING)
                .eq(CmGov809Outbox::getMsgType, POSITION_MSG_TYPE)
                .orderByAsc(CmGov809Outbox::getEventSeq)
                .last("LIMIT " + properties.getPruneBatch()));
        if (oldest.isEmpty()) {
            return;
        }
        List<Long> ids = oldest.stream().map(CmGov809Outbox::getId).toList();
        outboxMapper.update(null, Wrappers.<CmGov809Outbox>lambdaUpdate()
                .in(CmGov809Outbox::getId, ids)
                .set(CmGov809Outbox::getOutboxStatus, STATUS_DROPPED)
                .set(CmGov809Outbox::getLastError, "积压超过上限，定位报文被裁剪"));
        log.warn("809 积压超过上限，裁剪定位报文, platformId={}, dropped={}, maxPending={}",
                platformId, ids.size(), properties.getMaxPending());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
 
