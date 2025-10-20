package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单列表查询 dto
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title="OrderListDto", description ="订单列表查询")
public class OrderSimpleListDto {
    
    @Schema(name ="orderNumber", type ="Long", description ="订单编号")
    private Long orderNumber;
    
    @Schema(name ="userId", type ="Long", description ="用户id")
    private Long userId;
    
}
