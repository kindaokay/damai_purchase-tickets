package com.damai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.damai.data.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 分片路由映射 实体
 * @author: 阿星不是程序员
 **/
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("d_sharding_route_mapping")
public class ShardingRouteMapping extends BaseTableData implements Serializable {
    
    private Long id;
    
    /**
     * 逻辑分片ID（0-1023）
     */
    private Integer logicalShardId;
    
    /**
     * 物理数据库名后缀（0-1，适用于所有库类型）
     */
    private String physicalDatabaseSuffix;
    
    /**
     * 物理表后缀（0-7，适用于所有表类型）
     * 适用于：d_order_{suffix}、d_order_ticket_user_{suffix}、d_order_ticket_user_record_{suffix}
     */
    private Integer physicalTableSuffix;
    
    /**
     * 版本号（用于热更新）
     */
    private Integer version;
}
