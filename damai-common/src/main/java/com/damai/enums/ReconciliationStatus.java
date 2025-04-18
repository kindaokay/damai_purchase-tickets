package com.damai.enums;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 对账状态
 * @author: 阿星不是程序员
 **/
public enum ReconciliationStatus {
    /**
     * 对账状态
     * */
    RECONCILIATION_NO(1, "未对账"),
    RECONCILIATION_FAIL(-1,"对账完成有问题"),
    RECONCILIATION_SUCCESS(2,"对账完成没有问题"),
    RECONCILIATION_FINISH(3,"对账有问题处理完毕");
    
    private Integer code;
    
    private String msg = "";
    
    ReconciliationStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public Integer getCode() {
        return this.code;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        for (ReconciliationStatus re : ReconciliationStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }
    
    public static ReconciliationStatus getRc(Integer code) {
        for (ReconciliationStatus re : ReconciliationStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
