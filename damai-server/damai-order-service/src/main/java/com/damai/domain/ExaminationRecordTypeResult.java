package com.damai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: redis和数据对账结果(记录类型维度)
 * @author: 阿星不是程序员
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExaminationRecordTypeResult {

    /**
     * 记录类型编码 -1:扣减余票 0:改变状态 1:增加余票
     */
    private Integer recordTypeCode;

    /**
     * 记录类型值 -1:扣减余票(reduce) 0:改变状态(changeStatus) 1:增加余票(increase)
     * */
    private String recordTypeValue;

    /**
     * 座位的对账结果
     * */
    private ExaminationSeatResult examinationSeatResult;
}
