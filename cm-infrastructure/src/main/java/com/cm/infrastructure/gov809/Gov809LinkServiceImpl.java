package com.cm.infrastructure.gov809;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cm.infrastructure.persistence.entity.CmGov809Link;
import com.cm.infrastructure.persistence.entity.CmPlatformState;
import com.cm.infrastructure.persistence.mapper.CmGov809LinkMapper;
import com.cm.infrastructure.persistence.mapper.CmPlatformStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 链路状态与检查点实现。（platform_id, link_role）唯一，首次访问时插默认行；
 * 状态每次变更都写库，检查点只在定时刷盘与状态变更时写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Gov809LinkServiceImpl implements Gov809LinkService {

    private static final int ERROR_MAX_LENGTH = 255;

    private final CmGov809LinkMapper linkMapper;

    private final CmPlatformStateMapper platformStateMapper;

    @Override
    public Gov809LinkSnapshot load(long platformId, String role) {
        CmGov809Link row = requireRow(platformId, role);
        return new Gov809LinkSnapshot(platformId, role, row.getMainLinkState(), row.getSubLinkState(),
                valueOf(row.getLastSentSeq()), valueOf(row.getLastAckSeq()),
                valueOf(row.getPendingCount()), row.getLastError());
    }

    @Override
    public void markMainLink(long platformId, String role, String state, String error) {
        CmGov809Link row = requireRow(platformId, role);
        linkMapper.update(null, Wrappers.<CmGov809Link>lambdaUpdate()
                .eq(CmGov809Link::getId, row.getId())
                .set(CmGov809Link::getMainLinkState, state)
                .set(CmGov809Link::getMainLinkAt, LocalDateTime.now())
                .set(CmGov809Link::getLastError, truncate(error)));
        upsertPlatformState(platformId, state, null);
    }

    @Override
    public void markSubLink(long platformId, String role, String state, String error) {
        CmGov809Link row = requireRow(platformId, role);
        linkMapper.update(null, Wrappers.<CmGov809Link>lambdaUpdate()
                .eq(CmGov809Link::getId, row.getId())
                .set(CmGov809Link::getSubLinkState, state)
                .set(CmGov809Link::getSubLinkAt, LocalDateTime.now())
                .set(CmGov809Link::getLastError, truncate(error)));
        upsertPlatformState(platformId, null, state);
    }

    @Override
    public void flushCheckpoint(long platformId, String role, long maxAllocatedSeq, long lastSentSeq,
                                long pendingCount) {
        CmGov809Link row = requireRow(platformId, role);
        linkMapper.update(null, Wrappers.<CmGov809Link>lambdaUpdate()
                .eq(CmGov809Link::getId, row.getId())
                .set(CmGov809Link::getNextSeq, maxAllocatedSeq)
                .set(CmGov809Link::getLastSentSeq, lastSentSeq)
                .set(CmGov809Link::getLastAckSeq, lastSentSeq)
                .set(CmGov809Link::getPendingCount, pendingCount));
    }

    @Override
    public long maxAllocatedSeq(long platformId, String role) {
        return valueOf(requireRow(platformId, role).getNextSeq());
    }

    private CmGov809Link requireRow(long platformId, String role) {
        CmGov809Link row = linkMapper.selectOne(Wrappers.<CmGov809Link>lambdaQuery()
                .eq(CmGov809Link::getPlatformId, platformId)
                .eq(CmGov809Link::getLinkRole, role)
                .last("LIMIT 1"));
        if (row != null) {
            return row;
        }
        CmGov809Link created = new CmGov809Link();
        created.setPlatformId(platformId);
        created.setLinkRole(role);
        created.setMainLinkState(STATE_DISCONNECTED);
        created.setSubLinkState(STATE_DISCONNECTED);
        created.setNextSeq(0L);
        created.setLastSentSeq(0L);
        created.setLastAckSeq(0L);
        created.setPendingCount(0L);
        created.setLastError("");
        try {
            linkMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            log.debug("809 链路行已被其他线程创建, platformId={}, role={}", platformId, role);
            return linkMapper.selectOne(Wrappers.<CmGov809Link>lambdaQuery()
                    .eq(CmGov809Link::getPlatformId, platformId)
                    .eq(CmGov809Link::getLinkRole, role)
                    .last("LIMIT 1"));
        }
    }

    /**
     * 主从状态分两次更新时，另一次传 null 表示不改，避免后写入的 null 掩盖已在线的一侧。
     */
    private void upsertPlatformState(long platformId, String mainState, String subState) {
        CmPlatformState state = platformStateMapper.selectOne(Wrappers.<CmPlatformState>lambdaQuery()
                .eq(CmPlatformState::getPlatformId, platformId)
                .last("LIMIT 1"));
        boolean insert = state == null;
        if (insert) {
            state = new CmPlatformState();
            state.setPlatformId(platformId);
            state.setGpsServerDate(LocalDateTime.now());
            state.setGpsServerState("运行中");
            state.setMainLinkState(STATE_DISCONNECTED);
            state.setSubLinkState(STATE_DISCONNECTED);
        }
        if (mainState != null) {
            state.setMainLinkState(mainState);
            state.setMainLinkDate(LocalDateTime.now());
        }
        if (subState != null) {
            state.setSubLinkState(subState);
            state.setSubLinkDate(LocalDateTime.now());
        }
        if (insert) {
            platformStateMapper.insert(state);
        } else {
            platformStateMapper.updateById(state);
        }
    }

    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= ERROR_MAX_LENGTH ? value : value.substring(0, ERROR_MAX_LENGTH);
    }
}
 
