package com.damai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目票档详情 Vo
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title="TicketCategoryDbManageVo", description ="节目票档详情")
public class TicketCategoryDbManageVo {

    @Schema(name ="id", type ="Long", description ="节目票档id",requiredMode= RequiredMode.REQUIRED)
    private Long id;
    
    @Schema(name ="programId", type ="Long", description ="节目表id",requiredMode= RequiredMode.REQUIRED)
    private Long programId;
    
    @Schema(name ="introduce", type ="String", description ="介绍",requiredMode= RequiredMode.REQUIRED)
    private String introduce;
}
