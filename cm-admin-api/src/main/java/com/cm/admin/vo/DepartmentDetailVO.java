package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Data
@Schema(description = "车辆分组（部门）管理行，字段对齐旧 editDep 表单")
public class DepartmentDetailVO {

    @Schema(description = "部门 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "上级部门 ID，0 表示根部门", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "上级车组名称，根部门为空")
    private String parentName;

    @Schema(description = "车组名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "车组类型")
    private String departmentType;

    @Schema(description = "所属业户编号")
    private String memberNo;

    @Schema(description = "经营许可证字")
    private String roadPermitWord;

    @Schema(description = "经营许可证号")
    private String roadPermitNo;

    @Schema(description = "经营范围")
    private String businessScope;

    @Schema(description = "所属地区")
    private String region;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
