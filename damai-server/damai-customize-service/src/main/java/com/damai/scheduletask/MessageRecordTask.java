package com.damai.scheduletask;

import com.damai.BusinessThreadPool;
import com.damai.service.MessageRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 消息记录对账定时任务
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class MessageRecordTask {
    
    @Autowired
    private MessageRecordService messageRecordService;

    @Scheduled(cron = "0 0/5 * * * ? ")
    public void reconciliationTask(){
        BusinessThreadPool.execute( () -> {
            messageRecordService.executeReconciliationTask();
        });
    }
}
