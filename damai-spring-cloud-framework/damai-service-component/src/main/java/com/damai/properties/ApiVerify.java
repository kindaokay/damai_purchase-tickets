package com.damai.properties;

import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.util.StringUtil;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 后台管理配置属性
 * @author: 阿星不是程序员
 **/
public class ApiVerify {
    
    private final String apiPassword = "api_password";
    
    private final BackManageProperties backManageProperties;
    
    public ApiVerify(BackManageProperties backManageProperties) {
        this.backManageProperties = backManageProperties;
    }
    
    public void verifyApi() {
        if (backManageProperties.getApiPasswordCall()) {
            String password = Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                    .map(requestAttributes -> ((ServletRequestAttributes) requestAttributes).getRequest())
                    .map(request -> request.getHeader(apiPassword))
                    .orElseGet(() -> null);
            if (StringUtil.isEmpty(password)) {
                throw new DaMaiFrameException(BaseCode.API_CALL_NEED_PASSWORD);
            }
            if (!password.equals(backManageProperties.getApiPassword())) {
                throw new DaMaiFrameException(BaseCode.API_CALL_PASSWORD_ERROR);
            }
        }
    }
}
