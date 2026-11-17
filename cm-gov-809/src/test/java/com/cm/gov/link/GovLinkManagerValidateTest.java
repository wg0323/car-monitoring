package com.cm.gov.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cm.gov.config.GovSuperiorProperties;
import com.cm.infrastructure.gov809.Gov809LinkService;
import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.infrastructure.persistence.entity.CmPlatformInfo;
import com.cm.infrastructure.persistence.mapper.CmPlatformInfoMapper;
import com.cm.protocol.jt809.body.MainLinkLoginAck;
import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 登录校验四个分支：接入码未登记、账号不匹配、密码错误、通过。
 * 结果码与旧 UpConnectRsp.handle 一致：2 接入码、3 未注册、4 密码错。
 */
@ExtendWith(MockitoExtension.class)
class GovLinkManagerValidateTest {

    private static final long PLATFORM_ID = 9001L;

    private static final long USER_ID = 9001L;

    private static final String PASSWORD = "12345678";

    @Mock
    private Gov809LinkService linkService;

    @Mock
    private Jt809CommandLogService commandLogService;

    @Mock
    private CmPlatformInfoMapper platformInfoMapper;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private ThreadPoolExecutor businessExecutor;

    private GovLinkManager manager;

    @BeforeEach
    void setUp() {
        manager = new GovLinkManager(new GovSuperiorProperties(), linkService, commandLogService,
                platformInfoMapper, meterRegistry, null, businessExecutor);
    }

    @Test
    @DisplayName("接入码未登记时回 2")
    void should_reject_unknown_center() {
        when(platformInfoMapper.selectOne(any())).thenReturn(null);

        assertEquals(MainLinkLoginAck.RESULT_CENTER_INVALID,
                manager.validate(PLATFORM_ID, USER_ID, PASSWORD).result());
    }

    @Test
    @DisplayName("账号不匹配时回 3")
    void should_reject_unknown_user() {
        when(platformInfoMapper.selectOne(any())).thenReturn(platform("other"));

        assertEquals(MainLinkLoginAck.RESULT_USER_NOT_REGISTERED,
                manager.validate(PLATFORM_ID, USER_ID, PASSWORD).result());
    }

    @Test
    @DisplayName("密码错误时回 4")
    void should_reject_wrong_password() {
        when(platformInfoMapper.selectOne(any())).thenReturn(platform(String.valueOf(USER_ID)));

        assertEquals(MainLinkLoginAck.RESULT_PASSWORD_INVALID,
                manager.validate(PLATFORM_ID, USER_ID, "bad").result());
    }

    @Test
    @DisplayName("账号密码与接入码都匹配时通过，并回带接入码作为校验码")
    void should_accept_registered_platform() {
        when(platformInfoMapper.selectOne(any())).thenReturn(platform(String.valueOf(USER_ID)));

        MainLinkLoginAck ack = manager.validate(PLATFORM_ID, USER_ID, PASSWORD);

        assertEquals(MainLinkLoginAck.RESULT_SUCCESS, ack.result());
        assertEquals(PLATFORM_ID, ack.verifyCode());
    }

    private CmPlatformInfo platform(String userId) {
        CmPlatformInfo info = new CmPlatformInfo();
        info.setPlatformId(PLATFORM_ID);
        info.setUserId(userId);
        info.setPassword(PASSWORD);
        info.setUpstream(1);
        info.setIsEnabled(1);
        return info;
    }
}
 
