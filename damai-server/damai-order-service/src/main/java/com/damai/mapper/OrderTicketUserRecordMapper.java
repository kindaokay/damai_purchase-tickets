package com.damai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.entity.OrderTicketUserRecord;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 购票人订单记录 mapper
 * @author: 阿星不是程序员
 **/
public interface OrderTicketUserRecordMapper extends BaseMapper<OrderTicketUserRecord> {
    
    /**
     * 真实删除购票人订单记录数据
     * @return 结果
     * */
    Integer relDelOrderTicketUserRecord();

}
