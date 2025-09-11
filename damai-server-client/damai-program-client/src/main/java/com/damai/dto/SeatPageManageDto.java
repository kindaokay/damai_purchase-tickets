package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单查询 dto
 * @author: 阿星不是程序员
 **/
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(title="SeatPageManageDto", description ="座位")
public class SeatPageManageDto extends BasePageDto{
    
    @Schema(name ="节目id", type ="Long", description ="id",requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Long programId;
    
    @Schema(name ="票档id", type ="Long", description ="ticketCategoryId",requiredMode= RequiredMode.REQUIRED)
    private Long ticketCategoryId;
}
