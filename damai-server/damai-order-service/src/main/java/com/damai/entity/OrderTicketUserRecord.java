package com.damai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.damai.data.BaseTableData;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 购票人订单记录 实体
 * @author: 阿星不是程序员
 **/
@Data
@TableName("d_order_ticket_user_record")
public class OrderTicketUserRecord extends BaseTableData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    private Long id;
    
    /**
     * 订单编号
     * */
    private Long orderNumber;

    /**
     * 记录id
     */
    private Long identifierId;
    
    /**
     * 购票人订单id
     * */
    private Long ticketUserOrderId;

    /**
     * 节目表id
     */
    private Long programId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 购票人id
     */
    private Long ticketUserId;

    /**
     * 座位id
     */
    private Long seatId;
    
    /**
     * 座位信息
     * */
    private String seatInfo;
    
    /**
     * 节目票档id
     * */
    private Long ticketCategoryId;

    /**
     * 订单价格
     */
    private BigDecimal orderPrice;

    /**
     * 记录类型编码 -1:扣减余票 0:改变状态 1:增加余票
     */
    private Integer recordTypeCode;
    
    /**
     * 记录类型值 -1:扣减余票(reduce) 0:改变状态(changeStatus) 1:增加余票(increase)
     * */
    private String recordTypeValue;

    /**
     * 对账状态 1:未对账 -1:对账完成有问题 2:对账完成没有问题 3:对账有问题处理完毕
     */
    private Integer reconciliationStatus;
    
    /**
     * 创建类型 1:正常创建 2:补偿创建
     * */
    private Integer createType;
}
