package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Collection;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目票档集合 dto
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title="TicketCategoryListDto", description ="节目票档集合")
public class TicketCategoryListDto {
    
    @Schema(name ="programId", type ="Long", description ="节目id",requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Long programId;

    @Schema(name ="ticketCategoryIdList", type ="Long[]", description ="票档id集合",requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Collection<Long> ticketCategoryIdList;
}
