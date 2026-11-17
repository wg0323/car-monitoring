package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.ReportAlarmVO;
import com.cm.admin.vo.ReportOnlineRecordVO;
import com.cm.admin.vo.ReportOnlineVehicleVO;
import com.cm.admin.vo.ReportOptionsVO;

import java.io.OutputStream;
import java.time.LocalDateTime;

/**
 * 报表查询与导出。数据范围只接受登录主体 ID，列表与导出共用同一筛选口径。
 */
public interface ReportAppService {

    PageResult<ReportAlarmVO> alarmPage(Long userId, long page, long size, String plateNo, Long departmentId,
                                        String alarmType, String alarmSource,
                                        LocalDateTime startTime, LocalDateTime endTime);

    void exportAlarms(Long userId, String plateNo, Long departmentId, String alarmType, String alarmSource,
                      LocalDateTime startTime, LocalDateTime endTime, OutputStream outputStream);

    PageResult<ReportOnlineRecordVO> onlineRecordPage(Long userId, long page, long size, String plateNo,
                                                      Long departmentId, String eventType,
                                                      LocalDateTime startTime, LocalDateTime endTime);

    void exportOnlineRecords(Long userId, String plateNo, Long departmentId, String eventType,
                             LocalDateTime startTime, LocalDateTime endTime, OutputStream outputStream);

    PageResult<ReportOnlineVehicleVO> onlineVehiclePage(Long userId, long page, long size, String plateNo,
                                                        String simNo, Long departmentId, String status,
                                                        Integer offlineDays);

    void exportOnlineVehicles(Long userId, String plateNo, String simNo, Long departmentId, String status,
                              Integer offlineDays, OutputStream outputStream);

    PageResult<ReportAlarmVO> parkingPage(Long userId, long page, long size, String plateNo, Long departmentId,
                                          String alarmSource, LocalDateTime startTime, LocalDateTime endTime);

    void exportParkings(Long userId, String plateNo, Long departmentId, String alarmSource,
                        LocalDateTime startTime, LocalDateTime endTime, OutputStream outputStream);

    ReportOptionsVO options();
}
