package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 消息记录 dto
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title="MessageRecordDto", description ="消息记录")
public class MessageRecordDto extends BasePageDto {
    
    /**
     * 消息业务id
     */
    @Schema(name ="messageBusinessesId", type ="Long", description ="消息业务id", requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Long messageBusinessesId;
}
