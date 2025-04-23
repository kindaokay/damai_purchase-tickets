package com.damai.scheduletask;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson2.JSON;
import com.damai.BusinessThreadPool;
import com.damai.client.ProgramClient;
import com.damai.common.ApiResponse;
import com.damai.dto.ProgramRecordTaskListDto;
import com.damai.dto.ProgramRecordTaskUpdateDto;
import com.damai.enums.BaseCode;
import com.damai.enums.HandleStatus;
import com.damai.service.OrderTaskService;
import com.damai.util.DateUtils;
import com.damai.vo.ProgramRecordTaskVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 对账定时任务
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class ReconciliationTask {

    @Autowired
    private OrderTaskService orderTaskService;
    
    @Autowired
    private ProgramClient programClient;

    //@Scheduled(cron = "0 0/1 * * * ? ")
    public void reconciliationTask(){
        BusinessThreadPool.execute( () -> {
            try {
                log.info("对账任务执行");
                ProgramRecordTaskListDto programRecordTaskListDto = new ProgramRecordTaskListDto();
                programRecordTaskListDto.setHandleStatus(HandleStatus.NO_HANDLE.getCode());
                //查询当前时间前3分钟的对账记录
                programRecordTaskListDto.setCreateTime(DateUtils.addMinute(DateUtils.now(),-3));
                ApiResponse<List<ProgramRecordTaskVo>> listApiResponse = programClient.select(programRecordTaskListDto);
                if (!Objects.equals(listApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                    log.error("获取节目对账记录任务集合失败 dto : {} message: {}", JSON.toJSONString(programRecordTaskListDto),listApiResponse.getMessage());
                    return;
                }
                List<ProgramRecordTaskVo> programRecordTaskVoList = listApiResponse.getData();
                if (CollectionUtil.isEmpty(programRecordTaskVoList)) {
                    log.warn("获取节目对账记录任务集合为空 dto : {}",JSON.toJSONString(programRecordTaskListDto));
                    return;
                }
                Set<Long> programIdSet = new HashSet<>();
                Set<Date> createTimeSet = new HashSet<>();
                for (ProgramRecordTaskVo programRecordTaskVo : programRecordTaskVoList) {
                    programIdSet.add(programRecordTaskVo.getProgramId());
                    createTimeSet.add(programRecordTaskVo.getCreateTime());
                }
                for (Long programId : programIdSet) {
                    orderTaskService.reconciliationTask(programId);
                }
                //修改对账记录任务集合为已处理
                ProgramRecordTaskUpdateDto programRecordTaskUpdateDto = new ProgramRecordTaskUpdateDto();
                programRecordTaskUpdateDto.setBeforeHandleStatus(HandleStatus.NO_HANDLE.getCode());
                programRecordTaskUpdateDto.setAfterHandleStatus(HandleStatus.YES_HANDLE.getCode());
                programRecordTaskUpdateDto.setCreateTimeSet(createTimeSet);
                ApiResponse<Integer> updateApiResponse = programClient.update(programRecordTaskUpdateDto);
                if (!Objects.equals(listApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                    log.error("更新节目对账记录任务失败 dto : {} message: {}", JSON.toJSONString(programRecordTaskUpdateDto),updateApiResponse.getMessage());
                }
            }catch (Exception e) {
                log.error("reconciliation task error",e);
            }
        });
    }
}
