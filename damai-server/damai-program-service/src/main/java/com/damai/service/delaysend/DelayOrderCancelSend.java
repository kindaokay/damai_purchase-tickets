package com.damai.service.delaysend;

import com.alibaba.fastjson2.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.BusinessThreadPool;
import com.damai.client.ApiDataClient;
import com.damai.common.ApiResponse;
import com.damai.context.DelayQueueContext;
import com.damai.core.SpringUtil;
import com.damai.dto.DelayOrderCancelDto;
import com.damai.dto.InsertMessageProducerRecordDto;
import com.damai.dto.UpdateMessageProducerRecordDto;
import com.damai.enums.BaseCode;
import com.damai.enums.MessageSendStatus;
import com.damai.enums.MessageType;
import com.damai.module.DelayOrderCancelMessageModule;
import com.damai.vo.MessageProducerRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.damai.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME;
import static com.damai.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TIME_UNIT;
import static com.damai.constant.ProgramOrderConstant.DELAY_ORDER_CANCEL_TOPIC;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 延迟订单发送
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class DelayOrderCancelSend {
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private DelayQueueContext delayQueueContext;
    
    
    @Autowired
    private ApiDataClient apiDataClient;
    
    @Value("${delay.order.cancel:false}")
    private Boolean delayOrderCancel;
    
    public void sendMessage(DelayOrderCancelDto delayOrderCancelDto){
        if (!delayOrderCancel){
            return;
        }
        BusinessThreadPool.execute(() -> {
            Long messageTraceId = uidGenerator.getUid();
            Long messageId = uidGenerator.getUid();
            
            DelayOrderCancelMessageModule delayOrderCancelMessageModule = new DelayOrderCancelMessageModule();
            delayOrderCancelMessageModule.setMessageTraceId(messageTraceId);
            delayOrderCancelMessageModule.setMessageId(messageId);
            delayOrderCancelMessageModule.setProgramId(delayOrderCancelDto.getProgramId());
            delayOrderCancelMessageModule.setOrderNumber(delayOrderCancelDto.getOrderNumber());
            
            String messageContent = JSON.toJSONString(delayOrderCancelMessageModule);
            //记录消息发送日志
            InsertMessageProducerRecordDto insertMessageProducerRecordDto = new InsertMessageProducerRecordDto();
            insertMessageProducerRecordDto.setMessageType(MessageType.DELAY_ORDER_CANCEL.getCode());
            insertMessageProducerRecordDto.setMessageTraceId(messageTraceId);
            insertMessageProducerRecordDto.setMessageBusinessesId(delayOrderCancelMessageModule.getProgramId());
            insertMessageProducerRecordDto.setMessageId(messageId);
            insertMessageProducerRecordDto.setMessageTopic(SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC);
            insertMessageProducerRecordDto.setMessageContent(messageContent);
            ApiResponse<MessageProducerRecordVo> insertMessageProducerRecordApiResponse = apiDataClient.insertMessageProducerRecord(insertMessageProducerRecordDto);
            if (!insertMessageProducerRecordApiResponse.getCode().equals(BaseCode.SUCCESS.getCode())){
                log.error("添加记录消息发送日志失败，参数 : {}", JSON.toJSONString(insertMessageProducerRecordDto));
                return;
            }
            MessageProducerRecordVo messageProducerRecordVo = insertMessageProducerRecordApiResponse.getData();
            
            UpdateMessageProducerRecordDto updateMessageProducerRecordDto = new UpdateMessageProducerRecordDto();
            updateMessageProducerRecordDto.setId(messageProducerRecordVo.getId());
            
            try {
                log.info("延迟订单取消消息进行发送 消息体 : {}",messageContent);
                delayQueueContext.sendMessage(SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC,
                        messageContent, DELAY_ORDER_CANCEL_TIME, DELAY_ORDER_CANCEL_TIME_UNIT);
                //发送成功，更新发送成功状态
                updateMessageProducerRecordDto.setMessageSendStatus(MessageSendStatus.SEND_SUCCESS.getCode());
            }catch (Exception e) {
                log.error("send message error message : {}",messageContent,e);
                //发送失败，更新发送失败状态
                updateMessageProducerRecordDto.setMessageSendStatus(MessageSendStatus.SEND_FAIL.getCode());
                updateMessageProducerRecordDto.setMessageSendException(e.getMessage());
            }
            //更新消息发送
            apiDataClient.updateMessageProducerRecord(updateMessageProducerRecordDto);
        });
    }
}
