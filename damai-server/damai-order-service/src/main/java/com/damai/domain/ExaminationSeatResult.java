package com.damai.domain;

import com.damai.entity.OrderTicketUserRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: redis和数据对账结果(座位维度) - 以数据库为准
 * @author: 阿星不是程序员
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExaminationSeatResult {
    
    /**
     * Redis和数据库匹配的座位数量
     * */
    private int matchCount;

    /**
     * 需要向redis中补充的座位（数据库有但Redis没有）
     * */
    private List<OrderTicketUserRecord> needToRedisSeatRecordList;
}
