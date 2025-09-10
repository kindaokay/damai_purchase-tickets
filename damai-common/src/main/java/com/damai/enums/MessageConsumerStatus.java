package com.damai.enums;

import lombok.Getter;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 消息发送状态枚举
 * @author: 阿星不是程序员
 **/
@Getter
public enum MessageConsumerStatus {
    /**
     * 消息消费状态枚举
     * */
    UNCONSUMED(1,"未消费"),
    CONSUMER_FAIL(-1,"消费失败"),
    CONSUMER_SUCCESS(2,"消费成功"),
    ;

    private final Integer code;

    private final String msg;

    MessageConsumerStatus(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    
    public static String getMsg(Integer code) {
        if (code == null) {
            return "";
        }
        for (MessageConsumerStatus re : MessageConsumerStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re.msg;
            }
        }
        return "";
    }

    public static MessageConsumerStatus getRc(Integer code) {
        for (MessageConsumerStatus re : MessageConsumerStatus.values()) {
            if (re.code.intValue() == code.intValue()) {
                return re;
            }
        }
        return null;
    }
}
