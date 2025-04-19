package com.damai.enums;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 处理状态
 * @author: 阿星不是程序员
 **/

public enum HandleStatus {
    /**
     * 处理状态
     * */
    NO_HANDLE(1,"未处理"),
    YES_HANDLE(2,"已处理")
    ;

    private Integer code;

    private String msg;

    HandleStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }
    
    public String getMsg() {
        return this.msg == null ? "" : this.msg;
    }
    
    public static String getMsg(Integer code) {
        for (HandleStatus re : HandleStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    public static HandleStatus getRc(Integer code) {
        for (HandleStatus re : HandleStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
