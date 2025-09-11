package com.damai.service;


import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.damai.dto.ExecuteExceptionMessageDto;
import com.damai.dto.MessageRecordDto;
import com.damai.entity.MessageConsumerRecord;
import com.damai.entity.MessageProducerRecord;
import com.damai.enums.BaseCode;
import com.damai.enums.MessageConsumerStatus;
import com.damai.enums.MessageSendStatus;
import com.damai.enums.MessageType;
import com.damai.enums.ReconciliationStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.handler.ExceptionMessageHandlerContext;
import com.damai.mapper.MessageConsumerRecordMapper;
import com.damai.mapper.MessageProducerRecordMapper;
import com.damai.page.PageUtil;
import com.damai.reconciliation.ReconciliationTask;
import com.damai.reconciliation.ReconciliationTaskQueue;
import com.damai.util.DateUtils;
import com.damai.vo.MessageRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 消息记录实现层
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class MessageRecordService {
    
    @Autowired
    private MessageProducerRecordMapper messageProducerRecordMapper;
    
    @Autowired
    private MessageConsumerRecordMapper messageConsumerRecordMapper;
    
    @Autowired
    private ExceptionMessageHandlerContext exceptionMessageHandlerContext;
    
    @Autowired
    private ReconciliationTaskQueue reconciliationTaskQueue;
    
    @Autowired
    private MessageProducerRecordService messageProducerRecordService;
    
    
    public IPage<MessageRecordVo> page(MessageRecordDto messageRecordDto) {
        IPage<MessageRecordVo> messageRecordVoPage = new Page<>(messageRecordDto.getPageNumber(), messageRecordDto.getPageSize());
        //查看消息发送记录
        IPage<MessageProducerRecord> messageProducerRecordPage =
                messageProducerRecordMapper.selectPage(PageUtil.getPageParams(messageRecordDto.getPageNumber(),
                        messageRecordDto.getPageSize()), Wrappers.lambdaQuery(MessageProducerRecord.class)
                        .eq(MessageProducerRecord::getMessageBusinessesId, messageRecordDto.getMessageBusinessesId()));
        List<MessageProducerRecord> messageProducerRecordList = messageProducerRecordPage.getRecords();
        if (CollectionUtil.isEmpty(messageProducerRecordList)) {
            return messageRecordVoPage;
        }
        //消息发送记录的消息id查询对应的消息消费记录
        List<MessageConsumerRecord> messageConsumerRecordList = 
                messageConsumerRecordMapper.selectList(Wrappers.lambdaQuery(MessageConsumerRecord.class)
                        .in(MessageConsumerRecord::getMessageId, messageProducerRecordList.stream()
                                .map(MessageProducerRecord::getMessageId).toList()));
        //转成map结构，key:消息id，value：消息消费记录
        Map<Long, MessageConsumerRecord> messageConsumerRecordMap = messageConsumerRecordList.stream().collect(
                Collectors.toMap(MessageConsumerRecord::getMessageId, v -> v, 
                        (v1, v2) -> v2));
        List<MessageRecordVo> messageRecordVoList = new ArrayList<>();
        for (MessageProducerRecord messageProducerRecord : messageProducerRecordList) {
            MessageRecordVo messageRecordVo = new MessageRecordVo();
            BeanUtils.copyProperties(messageProducerRecord, messageRecordVo);
            messageRecordVo.setMessageProducerRecordId(messageProducerRecord.getId());
            messageRecordVo.setMessageTypeName(MessageType.getMsg(messageProducerRecord.getMessageType()));
            messageRecordVo.setMessageSendStatusName(MessageSendStatus.getMsg(messageProducerRecord.getMessageSendStatus()));
            messageRecordVo.setReconciliationStatusName(ReconciliationStatus.getMsg(messageProducerRecord.getReconciliationStatus()));
            MessageConsumerRecord messageConsumerRecord = messageConsumerRecordMap.get(messageProducerRecord.getMessageId());
            if (Objects.nonNull(messageConsumerRecord)) {
                messageRecordVo.setMessageConsumerRecordId(messageConsumerRecord.getId());
                messageRecordVo.setMessageConsumerException(messageConsumerRecord.getMessageConsumerException());
                messageRecordVo.setMessageConsumerStatus(messageConsumerRecord.getMessageConsumerStatus());
                messageRecordVo.setMessageConsumerStatusName(MessageConsumerStatus.getMsg(messageConsumerRecord.getMessageConsumerStatus()));
                messageRecordVo.setMessageConsumerCount(messageConsumerRecord.getMessageConsumerCount());
                messageRecordVo.setConsumerTime(messageConsumerRecord.getConsumerTime());
            }
            messageRecordVoList.add(messageRecordVo);
        }
        BeanUtils.copyProperties(messageProducerRecordPage, messageRecordVoPage);
        messageRecordVoPage.setRecords(messageRecordVoList);
        return messageRecordVoPage;
    }
    
    public Boolean executeExceptionMessage(ExecuteExceptionMessageDto executeExceptionMessageDto) {
        LambdaQueryWrapper<MessageProducerRecord> wrapper = Wrappers.lambdaQuery(MessageProducerRecord.class);
        wrapper.eq(MessageProducerRecord::getMessageId, executeExceptionMessageDto.getMessageId());
        MessageProducerRecord existMessageProducerRecord = messageProducerRecordMapper.selectOne(wrapper);
        if (Objects.isNull(existMessageProducerRecord)) {
            throw new DaMaiFrameException(BaseCode.MESSAGE_NOT_EXIST);
        }
        //如果已经是对账成功的消息，则直接返回成功
        if (ReconciliationStatus.RECONCILIATION_SUCCESS.getCode().equals(existMessageProducerRecord.getReconciliationStatus())) {
            return true;
        }
        MessageType messageType = MessageType.getRc(existMessageProducerRecord.getMessageType());
        if (Objects.isNull(messageType)) {
            throw new DaMaiFrameException(BaseCode.MESSAGE_TYPE_NOT_EXIST);
        }
        return exceptionMessageHandlerContext.getExceptionMessageHandler(messageType)
                .handle(existMessageProducerRecord);
    }
    
    public Boolean executeReconciliationTask() {
        log.info("执行消息记录的对账任务");
        for (MessageType messageType : MessageType.values()) {
            try {
                List<MessageProducerRecord> noReconciliationMessageProducerRecordList =
                        exceptionMessageHandlerContext.getExceptionMessageHandler(messageType).noReconciliationMessageProducerRecordList();
                if (CollectionUtil.isEmpty(noReconciliationMessageProducerRecordList)) {
                    continue;
                }
                //查询对应的消息消费记录
                //key:消息id，value:消息消费记录
                Map<Long, MessageConsumerRecord> messageConsumerRecordMap = getMessageConsumerRecordMap(noReconciliationMessageProducerRecordList.stream()
                        .map(MessageProducerRecord::getMessageId).toList());
                
                //执行对账的逻辑
                for (MessageProducerRecord messageProducerRecord : noReconciliationMessageProducerRecordList){
                    //获取对应的消费记录
                    MessageConsumerRecord messageConsumerRecord =
                            messageConsumerRecordMap.get(messageProducerRecord.getMessageId());
                    //没有消费记录，说明消息没有被消费，重新发送消息
                    //有消费记录，消费是未消费状态，说明有了消费记录后，直接宕机了，所以需要重新发送消息
                    //有消费记录，消费是失败状态，也需要重新发送消息
                    if (Objects.isNull(messageConsumerRecord) ||
                            messageConsumerRecord.getMessageConsumerStatus().equals(MessageConsumerStatus.UNCONSUMED.getCode()) ||
                            messageConsumerRecord.getMessageConsumerStatus().equals(MessageConsumerStatus.CONSUMER_FAIL.getCode())) {
                        ReconciliationTask reconciliationTask = () -> {
                            exceptionMessageHandlerContext.getExceptionMessageHandler(messageType).handle(messageProducerRecord);
                        };
                        //执行重新发送消息任务
                        reconciliationTaskQueue.putTask(reconciliationTask);
                    }else {
                        Integer messageSendStatus = messageProducerRecord.getMessageSendStatus();
                        Integer messageConsumerStatus = messageConsumerRecord.getMessageConsumerStatus();
                        //如果发送成功并且消费成功，那么更新对账成功
                        if (messageSendStatus.equals(MessageSendStatus.SEND_SUCCESS.getCode()) &&
                                messageConsumerStatus.equals(MessageConsumerStatus.CONSUMER_SUCCESS.getCode())) {
                            //更新对账成功
                            messageProducerRecordService.updateToReconciliationSuccess(messageProducerRecord,messageConsumerRecord);
                        }
                    }
                }
            }catch (Exception e){
                log.error("executeReconciliationTask error",e);
            }
        }
        return true;
    }
    
    /**
     * 查询对应的消息消费记录
     * */
    public Map<Long, MessageConsumerRecord> getMessageConsumerRecordMap(List<Long> messageIdList){
        LambdaQueryWrapper<MessageConsumerRecord> messageConsumerRecordWrapper = Wrappers.lambdaQuery(MessageConsumerRecord.class);
        messageConsumerRecordWrapper.in(MessageConsumerRecord::getMessageId, messageIdList);
        List<MessageConsumerRecord> messageConsumerRecordList = messageConsumerRecordMapper.selectList(messageConsumerRecordWrapper);
        return messageConsumerRecordList.stream().collect(Collectors.toMap(MessageConsumerRecord::getMessageId, m -> m));
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessageRecord(Date date){
        //把今天的消息记录数据删除掉，真实环境中不会删除的，这里是为了在线演示才删除的，要不然数据太多了
        String dateStr = DateUtils.format(date, DateUtils.FORMAT_DATE);
        messageProducerRecordMapper.deleteBySendTime(dateStr);
        messageConsumerRecordMapper.deleteByConsumerTime(dateStr);
    }
}
