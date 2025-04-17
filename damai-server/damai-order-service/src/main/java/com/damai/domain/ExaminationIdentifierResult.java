package com.damai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: redis和数据对账结果(记录标识维度)
 * @author: 阿星不是程序员
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExaminationIdentifierResult {

    /**
     * 记录标识
     * */
    private String identifierId;

    /**
     * 用户id
     * */
    private String userId;
    
    /**
     * 记录类型的集合
     * */
    List<ExaminationRecordTypeResult> examinationRecordTypeResultList;
}
