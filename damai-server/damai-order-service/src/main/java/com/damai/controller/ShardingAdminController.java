package com.damai.controller;

import com.damai.common.ApiResponse;
import com.damai.shardingsphere.ShardingExpansionService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/admin/sharding")
public class ShardingAdminController {
    
    @Autowired
    private ShardingExpansionService expansionService;
    
    /**
     * 执行扩容
     */
    @Operation(summary  = "执行扩容")
    @RequestMapping("/expand")
    public ApiResponse<Void> expand() {
        try {
            expansionService.executeExpansion();
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}