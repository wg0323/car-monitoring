package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.persistence.mapper.CmFunctionMapper;
import com.cm.infrastructure.persistence.row.MenuRow;
import com.cm.infrastructure.service.MenuQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 菜单与权限码查询实现。SQL 全部在 FunctionMapper.xml，本类不拼条件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuQueryServiceImpl implements MenuQueryService {

    private final CmFunctionMapper functionMapper;

    @Override
    public List<MenuRow> menuTree() {
        return functionMapper.selectMenuTree();
    }

    @Override
    public List<String> permissionCodes(Long userId, boolean superAdmin) {
        if (superAdmin) {
            log.debug("超管取全量权限码, userId={}", userId);
            return functionMapper.selectAllPermissionCodes();
        }
        if (userId == null) {
            return List.of();
        }
        return functionMapper.selectPermissionCodesByUser(userId);
    }
}
