package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.infrastructure.persistence.entity.CmUserDepartment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 用户部门关联 Mapper。
 */
public interface CmUserDepartmentMapper extends BaseMapper<CmUserDepartment> {

    /**
     * 授权并兼容历史逻辑删除行：旧行存在时只把 is_deleted 复活，不新增重复关联。
     */
    @Insert("INSERT INTO cm_user_department (id, user_id, department_id) "
            + "VALUES (#{id}, #{userId}, #{departmentId}) "
            + "ON DUPLICATE KEY UPDATE is_deleted = 0, update_time = CURRENT_TIMESTAMP(3)")
    int grant(@Param("id") long id, @Param("userId") long userId, @Param("departmentId") long departmentId);
}
