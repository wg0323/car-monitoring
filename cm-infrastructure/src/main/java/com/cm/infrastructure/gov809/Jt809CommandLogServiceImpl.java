package com.cm.infrastructure.gov809;

import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.persistence.mapper.CmJt809CommandMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 809 指令日志写入：任何持久化异常都不向链路层抛。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Jt809CommandLogServiceImpl implements Jt809CommandLogService {

    private final CmJt809CommandMapper commandMapper;

    @Override
    public void log(CmJt809Command row) {
        try {
            commandMapper.insert(row);
        } catch (RuntimeException ex) {
            log.warn("809 指令日志写入失败, platformId={}, cmd=0x{}", row.getPlatformId(),
                    Integer.toHexString(row.getCmd()), ex);
        }
    }
}
 
