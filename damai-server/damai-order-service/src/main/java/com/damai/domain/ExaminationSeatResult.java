package com.damai.domain;

import com.damai.entity.OrderTicketUserRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: redis和数据对账结果(座位维度)
 * @author: 阿星不是程序员
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExaminationSeatResult {

    /**
     * 以redis为准的座位记录统计数量
     * */
    private int redisStandardStatisticCount;
    
    /**
     * 以数据库为准的座位记录统计数量
     * */
    private int dbStandardStatisticCount;

    /**
     * 需要向数据库中补充的座位
     * */
    private List<SeatRecord> needToDbSeatRecordList;

    /**
     * 需要向redis中补充的座位
     * */
    private List<OrderTicketUserRecord> needToRedisSeatRecordList;
}
