package com.damai.controller;

import com.damai.common.ApiResponse;
import com.damai.dto.InsertMessageProducerRecordDto;
import com.damai.dto.UpdateMessageProducerRecordDto;
import com.damai.service.MessageProducerRecordService;
import com.damai.vo.MessageProducerRecordVo;
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
 * @description: 消息发送记录 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/message/producer/record")
@Tag(name = "/message/producer/record", description = "消息发送记录")
public class MessageProducerRecordController {

    @Autowired
    private MessageProducerRecordService messageProducerRecordService;
    
    @Operation(summary  = "添加消息发送记录")
    @PostMapping(value = "/insert")
    public ApiResponse<MessageProducerRecordVo> insertMessageProducerRecord(@Valid @RequestBody InsertMessageProducerRecordDto insertMessageProducerRecordDto) {
        return ApiResponse.ok(messageProducerRecordService.insertMessageProducerRecord(insertMessageProducerRecordDto));
    }
    
    @Operation(summary  = "更新消息发送记录")
    @PostMapping(value = "/update")
    public ApiResponse<Boolean> updateMessageProducerRecord(@Valid @RequestBody UpdateMessageProducerRecordDto updateMessageProducerRecordDto) {
        return ApiResponse.ok(messageProducerRecordService.updateMessageProducerRecord(updateMessageProducerRecordDto));
    }
}
