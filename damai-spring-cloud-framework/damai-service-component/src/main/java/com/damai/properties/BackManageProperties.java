package com.damai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 后台管理配置属性
 * @author: 阿星不是程序员
 **/
@Data
@ConfigurationProperties(prefix = BackManageProperties.MANAGE)
public class BackManageProperties {
    
    public static final String MANAGE = "manage";
    
    private String username = "admin";
    
    private String password = "admin";
    
    private List<String> loginExcludeApi = List.of("/auth/login");
    
    private Boolean apiPasswordCall = false;
    
    private String apiPassword;
}
