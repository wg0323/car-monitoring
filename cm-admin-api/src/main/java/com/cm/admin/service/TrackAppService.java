package com.cm.admin.service;

import com.cm.admin.vo.TrackResultVO;

import java.io.OutputStream;
import java.time.LocalDateTime;

/**
 * 历史轨迹用例。查询与导出使用同一份条件与点集，页面 total 与导出行数必须一致。
 */
public interface TrackAppService {

    TrackResultVO query(Long userId, long vehicleId, LocalDateTime startTime, LocalDateTime endTime);

    void export(Long userId, long vehicleId, LocalDateTime startTime, LocalDateTime endTime, OutputStream outputStream);
}
