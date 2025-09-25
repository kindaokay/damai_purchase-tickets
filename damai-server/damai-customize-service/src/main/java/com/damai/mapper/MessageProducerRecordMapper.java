package com.damai.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.entity.MessageProducerRecord;
import org.apache.ibatis.annotations.Delete;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 消息发送记录 mapper
 * @author: 阿星不是程序员
 **/
public interface MessageProducerRecordMapper extends BaseMapper<MessageProducerRecord> {
    
    /** 
     * 删除所有记录 
     * @return Integer 结果
     * */
    @Delete("DELETE FROM d_message_producer_record")
    Integer delete();
}
