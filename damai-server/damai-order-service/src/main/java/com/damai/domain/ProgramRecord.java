package com.damai.domain;

import lombok.Data;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: redis的操作记录
 * @author: 阿星不是程序员
 **/
@Data
public class ProgramRecord {
    
    private Long timestamp;
    
    private String recordType;
    
    private List<TicketCategoryRecord> ticketCategoryRecordList;
}
