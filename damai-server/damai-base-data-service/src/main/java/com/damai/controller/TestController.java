package com.damai.controller;

import com.damai.common.ApiResponse;
import com.damai.dto.ChannelDataAddDto;
import com.damai.service.ChannelDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 测试 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/test")
@Tag(name = "test-data", description = "测试")
public class TestController {
    
    @Autowired
    private ChannelDataService channelDataService;
    
    @Operation(summary = "测试")
    @PostMapping(value = "/test")
    public ApiResponse<Boolean> test(@Valid @RequestBody ChannelDataAddDto channelDataAddDto) {
        channelDataService.test(channelDataAddDto);
        return ApiResponse.ok(true);
    }
}
