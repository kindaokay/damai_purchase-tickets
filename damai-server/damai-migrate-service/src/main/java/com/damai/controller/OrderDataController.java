package com.damai.controller;

import com.damai.common.ApiResponse;
import com.damai.shardingsphere.powersoftwo.PowerOf2ScaleOutService;
import com.damai.shardingsphere.powersoftwo.ScaleOutValidationService;
import com.damai.shardingsphere.virtual.ShardingExpansionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单数据 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/order/data")
@Tag(name = "order/data", description = "订单数据")
public class OrderDataController {
    
    @Autowired
    private PowerOf2ScaleOutService powerOf2ScaleOutService;
    
    @Autowired
    private ScaleOutValidationService scaleOutValidationService;
    
    @Autowired
    private ShardingExpansionService expansionService;
    
    @Operation(summary  = "执行幂次方方式的扩容")
    @RequestMapping("/power/of/2/expand")
    public ApiResponse<Void> powerOf2Expand() {
        try {
            powerOf2ScaleOutService.executeScaleOut();
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @Operation(summary  = "验证幂次方方式的扩容")
    @RequestMapping("/power/of/2/verify")
    public ApiResponse<Void> powerOf2Verify() {
        try {
            scaleOutValidationService.validateDataDistribution();
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @Operation(summary  = "执行虚拟分片方式的扩容")
    @RequestMapping("/virtual/expand")
    public ApiResponse<Void> virtualExpand() {
        try {
            expansionService.executeExpansion();
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}