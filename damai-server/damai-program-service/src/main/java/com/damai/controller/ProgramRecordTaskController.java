package com.damai.controller;

import com.damai.common.ApiResponse;
import com.damai.dto.ProgramRecordTaskAddDto;
import com.damai.dto.ProgramRecordTaskListDto;
import com.damai.dto.ProgramRecordTaskUpdateDto;
import com.damai.service.ProgramRecordTaskService;
import com.damai.vo.ProgramRecordTaskVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目对账记录任务 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/program/record/task")
@Tag(name = "program-record-task", description = "节目对账记录任务")
public class ProgramRecordTaskController {
    
    @Autowired
    private ProgramRecordTaskService programRecordTaskService;
    
    
    @Operation(summary  = "获取节目对账记录任务集合")
    @PostMapping(value = "/select")
    public ApiResponse<List<ProgramRecordTaskVo>> select(@Valid @RequestBody ProgramRecordTaskListDto programRecordTaskListDto) {
        return ApiResponse.ok(programRecordTaskService.select(programRecordTaskListDto));
    }
    
    @Operation(summary  = "修改节目对账记录任务集合")
    @PostMapping(value = "/update")
    public ApiResponse<Integer> update(@Valid @RequestBody ProgramRecordTaskUpdateDto programRecordTaskUpdateDto) {
        return ApiResponse.ok(programRecordTaskService.updateByCreateTime(programRecordTaskUpdateDto));
    }
    
    @Operation(summary  = "添加节目对账记录任务")
    @PostMapping(value = "/add")
    public ApiResponse<Integer> add(@Valid @RequestBody ProgramRecordTaskAddDto orderTicketUserRecordAddDto) {
        return ApiResponse.ok(programRecordTaskService.add(orderTicketUserRecordAddDto));
    }
}
