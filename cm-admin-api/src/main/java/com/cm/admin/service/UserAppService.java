package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.UserDetailVO;
import com.cm.admin.vo.UserVO;

/**
 * 用户管理用例（P4-2 只读切片）。数据范围与服务端构造的 DataScope 绑定，部门筛选值必须落在范围内。
 */
public interface UserAppService {

    PageResult<UserVO> page(Long userId, long pageNo, long pageSize, String keyword, Long departmentId);

    /**
     * 详情。目标用户不在当前账号的数据范围内时抛 AccessDeniedException，与「用户不存在」同一出口。
     */
    UserDetailVO detail(Long userId, Long targetUserId);
}
