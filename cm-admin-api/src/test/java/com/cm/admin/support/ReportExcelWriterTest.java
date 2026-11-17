package com.cm.admin.support;

import com.alibaba.excel.EasyExcel;
import com.cm.admin.vo.ReportAlarmVO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出契约：写出的字节必须能被读回，表头中文不乱码，行数与输入一致。
 */
class ReportExcelWriterTest {

    @Test
    void should_write_readable_xlsx_with_chinese_header() throws IOException {
        ReportAlarmVO row = new ReportAlarmVO();
        row.setPlateNo("辽B00001");
        row.setPlateColorName("蓝色");
        row.setAlarmTypeName("超速报警");
        row.setAlarmSourceName("终端报警");
        row.setLocation("测试地点");
        row.setStartTimeText("2026-11-16 00:00:00");
        row.setEndTimeText("报警中");
        row.setTimeSpan(new BigDecimal("2.00"));
        row.setDepartmentName("测试车组");
        row.setVelocity(new BigDecimal("60.00"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ReportExcelWriter.write(out, "报警查询", ReportAlarmVO.class, List.of(row, row));

        assertTrue(out.size() > 0);
        List<Map<Integer, String>> all = EasyExcel.read(new ByteArrayInputStream(out.toByteArray()))
                .sheet()
                .headRowNumber(0)
                .doReadSync();
        assertEquals(3, all.size());
        assertEquals("车牌号", all.get(0).get(0));
        assertEquals("结束时间", all.get(0).get(6));
        assertEquals("辽B00001", all.get(1).get(0));
        assertEquals("报警中", all.get(1).get(6));
    }
}

