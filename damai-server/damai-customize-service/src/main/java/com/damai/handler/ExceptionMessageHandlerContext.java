package com.damai.handler;

import com.damai.enums.BaseCode;
import com.damai.enums.MessageType;
import com.damai.exception.DaMaiFrameException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 异常消息处理上下文
 * @author: 阿星不是程序员
 **/
@Component
public class ExceptionMessageHandlerContext {

    @Autowired
    private List<ExceptionMessageHandler> exceptionMessageHandlerList;
    
    private final Map<MessageType, ExceptionMessageHandler> exceptionMessageHandlerMap = new HashMap<>();
    
    @PostConstruct
    public void init() {
        for (ExceptionMessageHandler exceptionMessageHandler : exceptionMessageHandlerList) {
            exceptionMessageHandlerMap.put(exceptionMessageHandler.getMessageType(), exceptionMessageHandler);
        }
    }
    
    public ExceptionMessageHandler getExceptionMessageHandler(MessageType messageType) {
        return Optional.ofNullable(exceptionMessageHandlerMap.get(messageType)).orElseThrow(
                () -> new DaMaiFrameException(BaseCode.MESSAGE_TYPE_NOT_EXIST));
    }
}
