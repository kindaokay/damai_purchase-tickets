package com.damai.reconciliation;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 对账任务接口
 * @author: 阿星不是程序员
 **/
@FunctionalInterface
public interface ReconciliationTask {
    
    /***
     * 执行任务
     */
    void run();
}
