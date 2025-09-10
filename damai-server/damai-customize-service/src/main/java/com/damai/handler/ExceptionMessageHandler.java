package com.damai.handler;

import com.damai.entity.MessageProducerRecord;
import com.damai.enums.MessageType;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 异常消息处理
 * @author: 阿星不是程序员
 **/
public interface ExceptionMessageHandler {
    
    /**
     * 获取没有对账的消息发送记录集合
     * @return 结果
     * */
    List<MessageProducerRecord> noReconciliationMessageProducerRecordList();
    
    /**
     * 处理消息
     * @param messageProducerRecord 消息记录
     * @return 结果
     * */
    Boolean handle(MessageProducerRecord messageProducerRecord);
    
    /**
     * 获取消息类型
     * @return 结果
     * */
    MessageType getMessageType();
}
