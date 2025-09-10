package com.damai.module;

import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: DelayOrderCancelMessageModule
 * @author: 阿星不是程序员
 **/
@Data
public class DelayOrderCancelMessageModule {

    private Long messageTraceId;
    
    private Long messageId;
    
    private Long programId;
    
    private Long orderNumber;
}
