package com.damai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.damai.common.ApiResponse;
import com.damai.dto.ExecuteExceptionMessageDto;
import com.damai.dto.MessageRecordDto;
import com.damai.service.MessageRecordService;
import com.damai.vo.MessageRecordVo;
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
 * @description: 消息消费记录 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/message/record")
@Tag(name = "/message/record", description = "消息记录")
public class MessageRecordController {

    @Autowired
    private MessageRecordService messageRecordService;
    
    @Operation(summary  = "分页查询消息记录")
    @PostMapping(value = "/page")
    public ApiResponse<IPage<MessageRecordVo>> page(@Valid @RequestBody MessageRecordDto messageRecordDto) {
        return ApiResponse.ok(messageRecordService.page(messageRecordDto));
    }
    
    @Operation(summary  = "执行对账任务")
    @PostMapping(value = "/execute/Reconciliation/task")
    public ApiResponse<Boolean> executeReconciliationTask() {
        return ApiResponse.ok(messageRecordService.executeReconciliationTask());
    }
    
    @Operation(summary  = "处理异常消息")
    @PostMapping(value = "/execute/exception/message")
    public ApiResponse<Boolean> executeExceptionMessage(@Valid @RequestBody ExecuteExceptionMessageDto executeExceptionMessageDto) {
        return ApiResponse.ok(messageRecordService.executeExceptionMessage(executeExceptionMessageDto));
    }
}
