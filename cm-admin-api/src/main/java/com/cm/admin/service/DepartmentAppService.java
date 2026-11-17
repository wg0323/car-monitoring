package com.cm.admin.service;

import com.cm.admin.vo.DepartmentDetailVO;
import com.cm.admin.vo.DepartmentSaveRequest;
import com.cm.admin.vo.DepartmentVO;

import java.util.List;

/**
 * 部门用例。下拉只返回当前登录主体数据范围内的部门；
 * 车辆分组管理（TC-ADM-05）在同一份范围上做增删改，越权一律拒绝。
 */
public interface DepartmentAppService {

    List<DepartmentVO> list(Long userId);

    /**
     * 车辆分组管理树表，keyword 按名称前缀匹配。
     */
    List<DepartmentDetailVO> listManage(Long userId, String keyword);

    DepartmentDetailVO create(Long userId, DepartmentSaveRequest request);

    DepartmentDetailVO update(Long userId, Long id, DepartmentSaveRequest request);

    /**
     * 逻辑删除。分组下有车辆或有子组时拒绝，与旧系统 fakeDelete 一致。
     */
    void delete(Long userId, Long id);
}
