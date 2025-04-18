package com.damai.domain;

import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: redis的操作记录(座位层)
 * @author: 阿星不是程序员
 **/
@Data
public class SeatRecord {
    
    private Long ticketCategoryId;
    private Long seatId;
    private Long ticketUserId;
    private Integer beforeStatus;
    private Integer afterStatus;
}
