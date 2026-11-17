package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.FenceVO;
import com.cm.domain.fence.FenceCommand;

/**
 * 区域管理编排：数据范围解析在实现层完成，请求参数不带权限字段。
 */
public interface FenceAppService {

    PageResult<FenceVO> page(Long userId, long pageNo, long pageSize, String name, Long departmentId);

    FenceVO get(Long userId, long id);

    FenceVO create(Long userId, FenceCommand command);

    FenceVO update(Long userId, long id, FenceCommand command);

    void delete(Long userId, long id);
}
