package com.damai.service;


import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.dto.InsertMessageConsumerRecordDto;
import com.damai.dto.MessageIdDto;
import com.damai.dto.UpdateMessageConsumerRecordDto;
import com.damai.entity.MessageConsumerRecord;
import com.damai.enums.MessageConsumerStatus;
import com.damai.enums.ReconciliationStatus;
import com.damai.mapper.MessageConsumerRecordMapper;
import com.damai.util.DateUtils;
import com.damai.util.StringUtil;
import com.damai.vo.MessageConsumerRecordVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 消息消费服务实现层
 * @author: 阿星不是程序员
 **/
@Service
public class MessageConsumerRecordService extends ServiceImpl<MessageConsumerRecordMapper, MessageConsumerRecord> {
    
    @Autowired
    private UidGenerator uidGenerator;
    
    @Autowired
    private MessageConsumerRecordMapper messageConsumerRecordMapper;
    
    
    @Transactional(rollbackFor = Exception.class)
    public MessageConsumerRecordVo insertMessageConsumerRecord(InsertMessageConsumerRecordDto insertMessageConsumerRecordDto){
        MessageConsumerRecord messageConsumerRecord = new MessageConsumerRecord();
        messageConsumerRecord.setId(uidGenerator.getUid());
        messageConsumerRecord.setMessageType(insertMessageConsumerRecordDto.getMessageType());
        messageConsumerRecord.setMessageTraceId(insertMessageConsumerRecordDto.getMessageTraceId());
        messageConsumerRecord.setMessageBusinessesId(insertMessageConsumerRecordDto.getMessageBusinessesId());
        messageConsumerRecord.setMessageId(insertMessageConsumerRecordDto.getMessageId());
        messageConsumerRecord.setMessageTopic(insertMessageConsumerRecordDto.getMessageTopic());
        messageConsumerRecord.setMessageContent(insertMessageConsumerRecordDto.getMessageContent());
        messageConsumerRecord.setMessageConsumerStatus(MessageConsumerStatus.UNCONSUMED.getCode());
        messageConsumerRecord.setMessageConsumerCount(1);
        messageConsumerRecord.setReconciliationStatus(ReconciliationStatus.RECONCILIATION_NO.getCode());
        messageConsumerRecord.setConsumerTime(DateUtils.now());
        messageConsumerRecordMapper.insert(messageConsumerRecord);
        MessageConsumerRecordVo messageConsumerRecordVo = new MessageConsumerRecordVo();
        BeanUtils.copyProperties(messageConsumerRecord,messageConsumerRecordVo);
        return messageConsumerRecordVo;
    }
    
    public MessageConsumerRecord getMessageConsumerRecordByMessageId(Long messageId) {
        LambdaQueryWrapper<MessageConsumerRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageConsumerRecord::getMessageId, messageId);
        return messageConsumerRecordMapper.selectOne(wrapper);
    }
    
    public MessageConsumerRecordVo getByMessageId(MessageIdDto messageIdDto) {
        LambdaQueryWrapper<MessageConsumerRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageConsumerRecord::getMessageId, messageIdDto.getMessageId());
        MessageConsumerRecord messageConsumerRecord = messageConsumerRecordMapper.selectOne(wrapper);
        if (Objects.isNull(messageConsumerRecord)) {
            return null;
        }
        MessageConsumerRecordVo messageConsumerRecordVo = new MessageConsumerRecordVo();
        BeanUtils.copyProperties(messageConsumerRecord, messageConsumerRecordVo);
        return messageConsumerRecordVo;
    }
    
    public Boolean updateMessageConsumerRecord(UpdateMessageConsumerRecordDto updateMessageConsumerRecordDto) {
        MessageConsumerRecord updateMessageConsumerRecord = new MessageConsumerRecord();
        BeanUtils.copyProperties(updateMessageConsumerRecordDto,updateMessageConsumerRecord);
        if (StringUtil.isEmpty(updateMessageConsumerRecord.getMessageContent())) {
            updateMessageConsumerRecord.setMessageContent(null);
        }
        if (StringUtil.isEmpty(updateMessageConsumerRecord.getMessageConsumerException())) {
            updateMessageConsumerRecord.setMessageConsumerException(null);
        }
        return updateById(updateMessageConsumerRecord);
    }
}
