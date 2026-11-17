package com.cm.admin.converter;

import com.cm.admin.security.CmUserDetails;
import com.cm.admin.vo.DepartmentVO;
import com.cm.admin.vo.LoginUserVO;
import com.cm.admin.vo.MenuVO;
import com.cm.admin.vo.MonitorVehicleVO;
import com.cm.admin.vo.UserDetailVO;
import com.cm.admin.vo.UserRoleVO;
import com.cm.admin.vo.UserVO;
import com.cm.admin.vo.VehicleDetailVO;
import com.cm.admin.vo.VehicleVO;
import com.cm.infrastructure.monitor.PositionSnapshot;
import com.cm.infrastructure.persistence.row.DepartmentRow;
import com.cm.infrastructure.persistence.row.MenuRow;
import com.cm.infrastructure.persistence.row.UserRoleRow;
import com.cm.infrastructure.persistence.row.UserRow;
import com.cm.infrastructure.persistence.row.VehicleDetailRow;
import com.cm.infrastructure.persistence.row.VehicleRow;

import java.util.List;
import java.util.Map;

/**
 * 持久层行对象与领域主体到 API VO 的转换。集中在静态方法里，避免各 Service 各写一份逐字段拷贝。
 */
public final class ViewConverter {

    /** 808 经纬度为百万分之一度的整数。 */
    private static final double DEGREE_SCALE = 1_000_000.0;

    /** 808 速度为 0.1 公里/小时。 */
    private static final double SPEED_SCALE = 10.0;

    /** JT/T 808 车牌颜色码值；旧 basicData 字典未迁移，这里按标准码值展示。 */
    private static final Map<Integer, String> PLATE_COLOR_NAMES = Map.of(
            0, "蓝色",
            1, "黄色",
            2, "黑色",
            3, "白色",
            4, "绿色");

    private static final String PLATE_COLOR_OTHER = "其他";

    private ViewConverter() {
    }

    public static LoginUserVO toLoginUserVO(CmUserDetails principal) {
        LoginUserVO vo = new LoginUserVO();
        vo.setId(principal.getUserId());
        vo.setUsername(principal.getUsername());
        vo.setDisplayName(principal.getDisplayName());
        vo.setSuperAdmin(principal.isSuperAdmin());
        return vo;
    }

    public static MenuVO toMenuVO(MenuRow row) {
        MenuVO vo = new MenuVO();
        vo.setPermissionCode(row.getPermissionCode());
        vo.setName(row.getName());
        vo.setIcon(row.getIcon());
        vo.setMenuSort(row.getMenuSort());
        vo.setUrl(row.getUrl());
        return vo;
    }

    public static VehicleVO toVehicleVO(VehicleRow row) {
        VehicleVO vo = new VehicleVO();
        vo.setId(row.getId());
        vo.setPlateNo(row.getPlateNo());
        vo.setSimNo(row.getSimNo());
        vo.setDepartmentId(row.getDepartmentId());
        vo.setDepartmentName(row.getDepartmentName());
        vo.setVehicleType(row.getVehicleType());
        vo.setTerminalNo(row.getTerminalNo());
        vo.setDriver(row.getDriver());
        vo.setRunStatus(row.getRunStatus());
        return vo;
    }

    /**
     * 实时监控行。位置缺失（位置已过期或从未上报）时经纬度、速度、方向与设备时间全为 null，
     * 而不是给 0：0 度是几内亚湾，前端会把「无定位」画成「车在赤道」。
     */
    public static MonitorVehicleVO toMonitorVehicleVO(VehicleRow row, PositionSnapshot snapshot, boolean online) {
        MonitorVehicleVO vo = new MonitorVehicleVO();
        vo.setId(row.getId());
        vo.setPlateNo(row.getPlateNo());
        vo.setSimNo(row.getSimNo());
        vo.setDepartmentName(row.getDepartmentName());
        vo.setOnline(online);
        if (snapshot != null) {
            vo.setLatitude(snapshot.latitude() / DEGREE_SCALE);
            vo.setLongitude(snapshot.longitude() / DEGREE_SCALE);
            vo.setSpeedKph(Math.round(snapshot.speed() / SPEED_SCALE * 10.0) / 10.0);
            vo.setCourse(snapshot.course());
            vo.setDeviceTime(snapshot.deviceTime());
        }
        return vo;
    }

    public static DepartmentVO toDepartmentVO(DepartmentRow row) {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(row.getId());
        vo.setParentId(row.getParentId());
        vo.setName(row.getName());
        return vo;
    }

    public static VehicleDetailVO toVehicleDetailVO(VehicleDetailRow row) {
        VehicleDetailVO vo = new VehicleDetailVO();
        vo.setId(row.getId());
        vo.setPlateNo(row.getPlateNo());
        vo.setPlateColor(row.getPlateColor());
        vo.setPlateColorName(plateColorName(row.getPlateColor()));
        vo.setDepartmentId(row.getDepartmentId());
        vo.setDepartmentName(row.getDepartmentName());
        vo.setMemberNo(row.getMemberNo());
        vo.setTerminalNo(row.getTerminalNo());
        vo.setSimNo(row.getSimNo());
        vo.setVehicleType(row.getVehicleType());
        vo.setUseType(row.getUseType());
        vo.setIndustry(row.getIndustry());
        vo.setRegion(row.getRegion());
        vo.setRunStatus(row.getRunStatus());
        vo.setVehicleStatus(row.getVehicleStatus());
        vo.setDriver(row.getDriver());
        vo.setDriverMobile(row.getDriverMobile());
        vo.setMonitor(row.getMonitor());
        vo.setMonitorMobile(row.getMonitorMobile());
        vo.setMotorId(row.getMotorId());
        vo.setEngineNo(row.getEngineNo());
        vo.setFrameNo(row.getFrameNo());
        vo.setManufacture(row.getManufacture());
        vo.setModelNo(row.getModelNo());
        vo.setVendor(row.getVendor());
        vo.setGpsTerminalType(row.getGpsTerminalType());
        vo.setOperPermit(row.getOperPermit());
        vo.setRoutes(row.getRoutes());
        vo.setBuyTime(row.getBuyTime());
        vo.setInstallDate(row.getInstallDate());
        vo.setLastCheckTime(row.getLastCheckTime());
        vo.setOnlineTime(row.getOnlineTime());
        vo.setOfflineTime(row.getOfflineTime());
        vo.setStartDate(row.getStartDate());
        vo.setEndDate(row.getEndDate());
        vo.setWorkHour(row.getWorkHour());
        vo.setRemark(row.getRemark());
        return vo;
    }

    private static String plateColorName(Integer code) {
        if (code == null) {
            return "";
        }
        return PLATE_COLOR_NAMES.getOrDefault(code, PLATE_COLOR_OTHER);
    }

    public static UserVO toUserVO(UserRow row) {
        UserVO vo = new UserVO();
        vo.setId(row.getId());
        vo.setUsername(row.getUsername());
        vo.setDisplayName(row.getDisplayName());
        vo.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        vo.setPhoneNo(row.getPhoneNo());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    public static UserRoleVO toUserRoleVO(UserRoleRow row) {
        UserRoleVO vo = new UserRoleVO();
        vo.setRoleId(row.getRoleId());
        vo.setRoleCode(row.getRoleCode());
        vo.setRoleName(row.getRoleName());
        return vo;
    }

    /**
     * 详情由三份查询拼成：基本信息行、角色行、可访问部门行。
     * 部门复用 DepartmentVO，不另建一个字段完全相同的 VO。
     */
    public static UserDetailVO toUserDetailVO(UserRow row, List<UserRoleRow> roles, List<DepartmentRow> departments) {
        UserDetailVO vo = new UserDetailVO();
        vo.setId(row.getId());
        vo.setUsername(row.getUsername());
        vo.setDisplayName(row.getDisplayName());
        vo.setUserType(row.getUserType());
        vo.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        vo.setPhoneNo(row.getPhoneNo());
        vo.setCreateTime(row.getCreateTime());
        vo.setRoles(roles.stream().map(ViewConverter::toUserRoleVO).toList());
        vo.setDepartments(departments.stream().map(ViewConverter::toDepartmentVO).toList());
        return vo;
    }
}
