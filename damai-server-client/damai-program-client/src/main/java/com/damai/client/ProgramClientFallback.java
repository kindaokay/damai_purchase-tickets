package com.damai.client;

import com.damai.common.ApiResponse;
import com.damai.dto.*;
import com.damai.enums.BaseCode;
import com.damai.vo.ProgramRecordTaskVo;
import com.damai.vo.TicketCategoryDetailVo;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目服务 feign 异常
 * @author: 阿星不是程序员
 **/
@Component
public class ProgramClientFallback implements ProgramClient {
    
    @Override
    public ApiResponse<Boolean> operateSeatLockAndTicketCategoryRemainNumber(final ReduceRemainNumberDto reduceRemainNumberDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<List<TicketCategoryDetailVo>> selectList(final TicketCategoryListDto ticketCategoryDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<List<Long>> allList() {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<List<ProgramRecordTaskVo>> select(final ProgramRecordTaskListDto programRecordTaskListDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<Integer> update(final ProgramRecordTaskUpdateDto programRecordTaskUpdateDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<Integer> add(final ProgramRecordTaskAddDto orderTicketUserRecordAddDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
    
    @Override
    public ApiResponse<Boolean> operateProgramData(final ProgramOperateDataDto programOperateDataDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }

    @Override
    public ApiResponse<List<TicketCategoryDetailVo>> selectListByProgram(TicketCategoryListByProgramDto ticketCategoryListByProgramDto) {
        return ApiResponse.error(BaseCode.SYSTEM_ERROR);
    }
}
