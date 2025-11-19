package com.damai.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 丢弃的订单
 * @author: 阿星不是程序员
 **/
@Data
@NoArgsConstructor
public class DiscardOrder {
    /**
     * 参数信息
     * */
    private OrderCreateMq orderCreateMq;
    
    /**
     * 原因
     * */
    private Integer discardOrderReason;
    
    /**
     * 错误信息
     * */
    private String errorMsg;
    
    public DiscardOrder(OrderCreateMq orderCreateMq, Integer discardOrderReason) {
        this.orderCreateMq = orderCreateMq;
        this.discardOrderReason = discardOrderReason;
    }
    
    public DiscardOrder(OrderCreateMq orderCreateMq, Integer discardOrderReason, String errorMsg) {
        this.orderCreateMq = orderCreateMq;
        this.discardOrderReason = discardOrderReason;
        this.errorMsg = errorMsg;
    }
}
