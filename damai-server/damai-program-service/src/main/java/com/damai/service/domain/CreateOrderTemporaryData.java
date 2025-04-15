package com.damai.service.domain;

import com.damai.vo.SeatVo;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 创建订单临时需要的数据
 * @author: 阿星不是程序员
 **/
@Data
@AllArgsConstructor
public class CreateOrderTemporaryData {

    /**
     * 记录id
     */
    private Long identifierId;
    
    /**
     * 购买的座位
     * */
    private List<SeatVo> purchaseSeatList;
   
}
