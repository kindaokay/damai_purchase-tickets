package com.damai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 丢弃的订单
 * @author: 阿星不是程序员
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscardOrder {
    
    /**
     * 节目id
     * */
    private Long programId;
    
    /**
     * key: 节目票档id value: 座位id集合
     * */
    private Map<Long, List<Long>> seatMap;
    
    /**
     * 订单状态
     * */
    private Integer orderStatus;
}
