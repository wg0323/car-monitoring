package com.cm.admin.service.impl;

import com.cm.admin.service.SystemInfoService;
import com.cm.admin.vo.SystemInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class SystemInfoServiceImpl implements SystemInfoService {

    private static final String STAGE = "P5";

    private static final String DEFAULT_PROFILE = "default";

    private final Environment environment;

    @Override
    public SystemInfoVO describe() {
        String[] profiles = environment.getActiveProfiles();
        SystemInfoVO vo = new SystemInfoVO();
        vo.setApplicationName(environment.getProperty("spring.application.name", "cm-admin-api"));
        vo.setStage(STAGE);
        vo.setActiveProfiles(profiles.length == 0 ? DEFAULT_PROFILE : String.join(",", Arrays.asList(profiles)));
        vo.setJavaVersion(System.getProperty("java.version"));
        return vo;
    }
}
