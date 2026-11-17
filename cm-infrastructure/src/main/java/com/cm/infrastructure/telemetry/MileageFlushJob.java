package com.cm.infrastructure.telemetry;

import com.cm.infrastructure.persistence.entity.CmMileageStatistic;
import com.cm.infrastructure.service.CmMileageStatisticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 里程定时落库。只处理当日累计发生过变化的车辆：没变化的车不重复写库。
 * 写库是绝对值覆盖，flush 重跑、网关重启、重复执行结果都一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.telemetry.mileage", name = "flush-enabled", havingValue = "true")
public class MileageFlushJob {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private static final char KEY_SEPARATOR = ':';

    private final MileageStore mileageStore;

    private final CmMileageStatisticService mileageStatisticService;

    private final TelemetryMetrics metrics;

    @Scheduled(fixedDelayString = "${cm.telemetry.mileage.flush-interval-millis:60000}",
            initialDelayString = "${cm.telemetry.mileage.flush-initial-delay-millis:30000}")
    public void flush() {
        int flushed = 0;
        for (String key : mileageStore.dayKeys()) {
            MileageStore.MileageDay day = mileageStore.readDay(key);
            if (day == null || !day.flushable()) {
                continue;
            }
            if (day.flushed() != null && day.flushed().compareTo(day.total()) == 0) {
                continue;
            }
            try {
                mileageStatisticService.upsertDaily(toRecord(key, day));
                mileageStore.markFlushed(key, day.total());
                flushed++;
            } catch (RuntimeException ex) {
                // 单台车失败不影响其他车；下一轮还会看到 total 与 flushed 不一致而重试。
                log.error("里程落库失败，下轮重试, key={}", key, ex);
            }
        }
        if (flushed > 0) {
            metrics.mileageFlushed(flushed);
            log.info("里程落库完成, flushed={}", flushed);
        }
    }

    private CmMileageStatistic toRecord(String key, MileageStore.MileageDay day) {
        String dayText = key.substring(key.lastIndexOf(KEY_SEPARATOR) + 1);
        CmMileageStatistic record = new CmMileageStatistic();
        record.setCompanyId(day.companyId());
        record.setDepartmentId(day.departmentId());
        record.setDepartmentName("");
        record.setPlateNo(day.plateNo());
        record.setPeriodType(CmMileageStatistic.PERIOD_DAY);
        record.setPeriodDescr(dayText);
        record.setStatisticDate(LocalDate.parse(dayText, DAY_FORMAT).atStartOfDay());
        record.setWorkHour(BigDecimal.ZERO);
        record.setMileage(day.total());
        record.setMileage1(day.first());
        record.setMileage2(day.last());
        return record;
    }
}
