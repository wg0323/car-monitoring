package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "新建或修改车辆分组的请求体，字段对齐旧 editDep 表单")
public class DepartmentSaveRequest {

    @Schema(description = "车组名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "大连港车队")
    @NotBlank(message = "车组名称不能为空")
    @Size(max = 64, message = "车组名称不能超过 64 个字符")
    private String name;

    @Schema(description = "上级车组 ID，0 表示根部门", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "0")
    private Long parentId;

    @Schema(description = "车组类型；旧 groupType 字典未迁移，按文本保存", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 32, message = "车组类型不能超过 32 个字符")
    private String departmentType;

    @Schema(description = "所属业户编号", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 64, message = "所属业户编号不能超过 64 个字符")
    private String memberNo;

    @Schema(description = "经营许可证字", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 64, message = "经营许可证字不能超过 64 个字符")
    private String roadPermitWord;

    @Schema(description = "经营许可证号", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 64, message = "经营许可证号不能超过 64 个字符")
    private String roadPermitNo;

    @Schema(description = "经营范围", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "经营范围不能超过 255 个字符")
    private String businessScope;

    @Schema(description = "所属地区", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 64, message = "所属地区不能超过 64 个字符")
    private String region;

    @Schema(description = "联系人", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 64, message = "联系人不能超过 64 个字符")
    private String contactPerson;

    @Schema(description = "联系电话", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 32, message = "联系电话不能超过 32 个字符")
    private String contactPhone;

    @Schema(description = "备注", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
