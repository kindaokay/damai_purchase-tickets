package com.damai.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.entity.MessageConsumerRecord;
import org.apache.ibatis.annotations.Delete;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 消息消费记录 mapper
 * @author: 阿星不是程序员
 **/
public interface MessageConsumerRecordMapper extends BaseMapper<MessageConsumerRecord> {
    
    /**
     * 根据消费时间删除数据
     * @param consumerTime 消费时间
     * @return 删除数量
     */
    @Delete("DELETE FROM d_message_consumer_record where DATE_FORMAT(consumer_time, '%Y-%m-%d') = #{consumerTime}")
    Integer deleteByConsumerTime(String consumerTime);
}
