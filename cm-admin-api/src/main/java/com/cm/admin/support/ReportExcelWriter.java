package com.cm.admin.support;

import com.alibaba.excel.EasyExcel;

import java.io.OutputStream;
import java.util.List;

/**
 * Excel 导出的唯一出口。业务代码只依赖本类，后续平移 Apache Fesod 时只改这里的依赖与包名。
 */
public final class ReportExcelWriter {

    private ReportExcelWriter() {
    }

    public static <T> void write(OutputStream outputStream, String sheetName, Class<T> head, List<T> rows) {
        EasyExcel.write(outputStream, head).sheet(sheetName).doWrite(rows);
    }
}
