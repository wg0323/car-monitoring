package com.cm.domain.scope;

import com.cm.domain.exception.CmBusinessException;

import java.util.List;
import java.util.Objects;

/**
 * 服务端构造的数据范围。列表、报表与导出查询共用，禁止由 HTTP 请求参数反序列化得到。
 * 使用标准 getter 而不是 record，保证 MyBatis OGNL 在 XML 里能稳定解析属性。
 */
public final class DataScope {

    /** 单次 IN 条件允许的最大元素数，超过时改走部门树子查询。 */
    public static final int MAX_SCOPE_IDS = 1000;

    private static final String ADMIN_USER_TYPE = "admin";

    private final Long userId;

    private final boolean superAdmin;

    private final List<Long> companyIds;

    private final List<Long> departmentIds;

    public DataScope(Long userId, boolean superAdmin, List<Long> companyIds, List<Long> departmentIds) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(companyIds, "companyIds must not be null");
        Objects.requireNonNull(departmentIds, "departmentIds must not be null");
        if (companyIds.size() > MAX_SCOPE_IDS || departmentIds.size() > MAX_SCOPE_IDS) {
            throw new CmBusinessException("数据范围超过 " + MAX_SCOPE_IDS + " 条，需改走部门树子查询");
        }
        if (!superAdmin && (departmentIds.isEmpty() || companyIds.isEmpty())) {
            throw new CmBusinessException("当前账号未配置可访问部门或企业");
        }
        this.userId = userId;
        this.superAdmin = superAdmin;
        this.companyIds = List.copyOf(companyIds);
        this.departmentIds = List.copyOf(departmentIds);
    }

    /** 只有旧库 userinfo.userType=admin 的账号才是超管，见 P0 实测的 sa/123321 口径。 */
    public static boolean isAdminUserType(String userType) {
        return ADMIN_USER_TYPE.equalsIgnoreCase(userType == null ? "" : userType.trim());
    }

    public static DataScope superAdmin(Long userId) {
        return new DataScope(userId, true, List.of(), List.of());
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public List<Long> getCompanyIds() {
        return companyIds;
    }

    public List<Long> getDepartmentIds() {
        return departmentIds;
    }

    /** 是否需要按部门拼接过滤条件；超管走无条件分支，不传空集合。 */
    public boolean hasDepartmentFilter() {
        return !superAdmin;
    }

    /**
     * 部门筛选值是否落在数据范围内。null 视为不筛选，超管不限；
     * 与列表查询同一口径：请求值只与范围比对，不合并进权限参数。
     */
    public boolean allowsDepartment(Long departmentId) {
        return departmentId == null || superAdmin || departmentIds.contains(departmentId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataScope)) {
            return false;
        }
        DataScope that = (DataScope) other;
        return superAdmin == that.superAdmin
                && Objects.equals(userId, that.userId)
                && Objects.equals(companyIds, that.companyIds)
                && Objects.equals(departmentIds, that.departmentIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, superAdmin, companyIds, departmentIds);
    }

    @Override
    public String toString() {
        return "DataScope{userId=" + userId
                + ", superAdmin=" + superAdmin
                + ", companyIds=" + companyIds.size()
                + ", departmentIds=" + departmentIds.size() + '}';
    }
}
