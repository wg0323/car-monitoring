package com.cm.infrastructure.service;

import com.cm.domain.scope.DataScope;

/**
 * 服务端数据范围构造。只接受认证主体的用户 ID，不接受请求参数里的 depIdList / companyIdList / superAdmin。
 */
public interface DataScopeService {

    DataScope resolve(Long userId);
}
