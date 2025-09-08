package com.damai.controller;

import com.damai.common.ApiResponse;
import com.damai.dto.BackManageLoginDto;
import com.damai.service.BackManageUserService;
import com.damai.vo.BackManageLoginVo;
import com.damai.vo.BackManageUserDetailVo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 后台登录 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/auth")
@Tag(name = "manage", description = "后台")
public class ManageController {
    
    @Autowired
    private BackManageUserService backManageUserService;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<BackManageLoginVo> login(@Valid @RequestBody BackManageLoginDto backManageLoginDto) {
        return ApiResponse.ok(backManageUserService.login(backManageLoginDto));
    }
    
    /**
     * 查询用户信息
     */
    @GetMapping("/user/info")
    public ApiResponse<BackManageUserDetailVo> getUser() {
        return ApiResponse.ok(backManageUserService.userInfo());
    }
}
