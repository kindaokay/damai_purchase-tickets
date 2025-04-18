package com.damai.scheduletask;

import com.damai.BusinessThreadPool;
import com.damai.client.ProgramClient;
import com.damai.common.ApiResponse;
import com.damai.enums.BaseCode;
import com.damai.service.OrderTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

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

    @Scheduled(cron = "0 0/1 * * * ? ")
    public void reconciliationTask(){
        BusinessThreadPool.execute( () -> {
            try {
                log.info("对账任务执行");
                ApiResponse<List<Long>> programApiResponse = programClient.allList();
                if (!Objects.equals(programApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                    log.info("获取节目id集合失败 message: {}",programApiResponse.getMessage());
                    return;
                }
                List<Long> programIdList = programApiResponse.getData();
                for (Long programId : programIdList) {
                    orderTaskService.reconciliationTask(programId);
                }
            }catch (Exception e) {
                log.error("reconciliation task error",e);
            }
        });
    }
}
