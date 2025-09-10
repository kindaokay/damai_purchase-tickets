package com.damai.service.delayconsumer;

import com.alibaba.fastjson.JSON;
import com.damai.client.ApiDataClient;
import com.damai.common.ApiResponse;
import com.damai.core.ConsumerTask;
import com.damai.core.SpringUtil;
import com.damai.dto.InsertMessageConsumerRecordDto;
import com.damai.dto.MessageIdDto;
import com.damai.dto.OrderCancelDto;
import com.damai.dto.UpdateMessageConsumerRecordDto;
import com.damai.enums.BaseCode;
import com.damai.enums.MessageConsumerStatus;
import com.damai.enums.MessageType;
import com.damai.exception.DaMaiFrameException;
import com.damai.module.DelayOrderCancelMessageModule;
import com.damai.service.OrderService;
import com.damai.util.DateUtils;
import com.damai.util.StringUtil;
import com.damai.vo.MessageConsumerRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.damai.service.constant.OrderConstant.DELAY_ORDER_CANCEL_TOPIC;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 延迟订单取消
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class DelayOrderCancelConsumer implements ConsumerTask {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private ApiDataClient apiDataClient;
    
    
    @Override
    public void execute(String content) {
        log.info("延迟订单取消消息进行消费 content : {}", content);
        if (StringUtil.isEmpty(content)) {
            log.error("延迟队列消息不存在");
            return;
        }
        DelayOrderCancelMessageModule delayOrderCancelMessageModule = JSON.parseObject(content, DelayOrderCancelMessageModule.class);
        
        Long messageTraceId = delayOrderCancelMessageModule.getMessageTraceId();
        Long messageId = delayOrderCancelMessageModule.getMessageId();
        Long programId = delayOrderCancelMessageModule.getProgramId();
        Long orderNumber = delayOrderCancelMessageModule.getOrderNumber();
        
        MessageIdDto messageIdDto = new MessageIdDto();
        messageIdDto.setMessageId(messageId);
        ApiResponse<MessageConsumerRecordVo> apiResponse = apiDataClient.getMessageConsumerByMessageId(messageIdDto);
        if (!apiResponse.getCode().equals(BaseCode.SUCCESS.getCode())) {
            log.error("查询消息消费记录失败 messageId : {}",messageId);
            return;
        }
        
        //查询已存在的消息消费记录
        MessageConsumerRecordVo existMessageConsumerRecordVo = apiResponse.getData();
        
        //如果消息已经被成功消费过，直接确认消息，避免重复消费
        if (Objects.nonNull(existMessageConsumerRecordVo) &&
                existMessageConsumerRecordVo.getMessageConsumerStatus().equals(MessageConsumerStatus.CONSUMER_SUCCESS.getCode())) {
            return;
        }
        Long messageConsumerRecordId = null;
        //消息的消费次数
        Integer messageConsumerCount;
        // 如果是第一次消费该消息，记录消息到数据库
        if (Objects.isNull(existMessageConsumerRecordVo)) {
            // 记录消息添加
            InsertMessageConsumerRecordDto insertMessageConsumerRecordDto = new InsertMessageConsumerRecordDto();
            insertMessageConsumerRecordDto.setMessageId(messageId);
            insertMessageConsumerRecordDto.setMessageTraceId(messageTraceId);
            insertMessageConsumerRecordDto.setMessageType(MessageType.DELAY_ORDER_CANCEL.getCode());
            insertMessageConsumerRecordDto.setMessageBusinessesId(programId);
            insertMessageConsumerRecordDto.setMessageTopic(SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC);
            insertMessageConsumerRecordDto.setMessageContent(content);
            ApiResponse<MessageConsumerRecordVo> insertApiResponse = apiDataClient.insertMessageConsumerRecord(insertMessageConsumerRecordDto);
            if (!insertApiResponse.getCode().equals(BaseCode.SUCCESS.getCode())) {
                log.error("添加消息消费记录失败 insertMessageConsumerRecordDto : {}", JSON.toJSONString(insertMessageConsumerRecordDto));
                return;
            }
            MessageConsumerRecordVo saveMessageConsumerRecordVo = insertApiResponse.getData();
            messageConsumerRecordId = saveMessageConsumerRecordVo.getId();
            messageConsumerCount = saveMessageConsumerRecordVo.getMessageConsumerCount();
        }else {
            messageConsumerRecordId = existMessageConsumerRecordVo.getId();
            //如果消费记录存在了，说明不是第一次消费该消息，那么消费次数+1
            messageConsumerCount = existMessageConsumerRecordVo.getMessageConsumerCount() + 1;
        }
        //根据业务执行的成功或者失败来更新消费记录
        UpdateMessageConsumerRecordDto updateMessageConsumerRecordDto = new UpdateMessageConsumerRecordDto();
        updateMessageConsumerRecordDto.setId(messageConsumerRecordId);
        updateMessageConsumerRecordDto.setMessageConsumerCount(messageConsumerCount);
        updateMessageConsumerRecordDto.setConsumerTime(DateUtils.now());
        
        try {
            //取消订单
            OrderCancelDto orderCancelDto = new OrderCancelDto();
            orderCancelDto.setOrderNumber(orderNumber);
            boolean cancel = orderService.cancel(orderCancelDto);
            if (cancel) {
                log.info("延迟订单取消成功 orderCancelDto : {}",content);
                updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_SUCCESS.getCode());
            }else {
                log.error("延迟订单取消失败 orderCancelDto : {}",content);
                updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_FAIL.getCode());
                updateMessageConsumerRecordDto.setMessageConsumerException("订单取消失败");
            }
        } catch (DaMaiFrameException e) {
            //对于这种业务异常，消费记录也算成功，避免消息一直重试消费
            updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_SUCCESS.getCode());
        } catch (Exception e) {
            updateMessageConsumerRecordDto.setMessageConsumerStatus(MessageConsumerStatus.CONSUMER_FAIL.getCode());
            updateMessageConsumerRecordDto.setMessageConsumerException(e.getMessage());
        }
        apiDataClient.updateMessageConsumerRecord(updateMessageConsumerRecordDto);
    }
    
    @Override
    public String topic() {
        return SpringUtil.getPrefixDistinctionName() + "-" + DELAY_ORDER_CANCEL_TOPIC;
    }
}
