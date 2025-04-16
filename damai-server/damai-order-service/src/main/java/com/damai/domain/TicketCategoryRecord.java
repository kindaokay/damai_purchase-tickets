package com.damai.domain;

import lombok.Data;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: redis的操作记录(票档层)
 * @author: 阿星不是程序员
 **/
@Data
public class TicketCategoryRecord {
    
    private Long ticketCategoryId;
    private Long beforeAmount;
    private Long afterAmount;
    private Long changeAmount;
    private List<SeatRecord> seatRecordList;
}
