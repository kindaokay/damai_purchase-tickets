package com.damai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: redis和数据对账结果(节目维度)
 * @author: 阿星不是程序员
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExaminationProgramResult {
    
    /**
     * 记录标识的集合
     * */
    private List<ExaminationIdentifierResult> examinationIdentifierResultList;
}
