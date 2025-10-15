package com.damai.shardingsphere.algorithm;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.core.SpringUtil;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.shardingsphere.VirtualShardingRouteManager;
import com.damai.shardingsphere.VirtualShardingRouteManager.PhysicalShard;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * 基于虚拟分片的分表算法
 */
public class TableOrderVirtualShardingAlgorithm implements ComplexKeysShardingAlgorithm<Long> {
    
    
    @Override
    public void init(Properties props) {
        // 无需初始化
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> allActualSplitTableNames, 
                                         ComplexKeysShardingValue<Long> complexKeysShardingValue) {
        //返回的真实表名集合
        List<String> actualTableNames = new ArrayList<>();
        //查询中的列名和值
        Map<String, Collection<Long>> columnNameAndShardingValuesMap = 
                complexKeysShardingValue.getColumnNameAndShardingValuesMap();
        
        //无条件查询 → 返回空（由分库算法决定）
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return actualTableNames;
        }
        
        //获取分片键
        //order_number条件的值
        Collection<Long> orderNumberValues = columnNameAndShardingValuesMap.get("order_number");
        //user_id条件的值
        Collection<Long> userIdValues = columnNameAndShardingValuesMap.get("user_id");
        
        Long shardingKey = null;
        //如果是order_number查询
        if (CollectionUtil.isNotEmpty(orderNumberValues)) {
            shardingKey = orderNumberValues.stream().findFirst()
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
        } else if (CollectionUtil.isNotEmpty(userIdValues)) {
            //如果是user_id查询
            shardingKey = userIdValues.stream().findFirst()
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
        }
        
        if (Objects.nonNull(shardingKey)) {
            // 通过路由管理器获取物理分片
            PhysicalShard physicalShard = SpringUtil.getBean(VirtualShardingRouteManager.class)
                    .route(shardingKey);
            
            if (physicalShard != null) {
                // 获取逻辑表名（ShardingSphere提供）
                String logicTableName = complexKeysShardingValue.getLogicTableName();
                // 根据逻辑表名生成完整的物理表名
                // 例如：逻辑表 t_order → 物理表 d_order_0
                String fullTableName = physicalShard.getFullTableName(logicTableName);
                
                actualTableNames.add(fullTableName);
            }
            return actualTableNames.isEmpty() ? allActualSplitTableNames : actualTableNames;
        }
        return allActualSplitTableNames;
    }
    
    /**
     * 将逻辑表名转换为物理表前缀
     * 例如：t_order → d_order
     *      t_order_ticket_user → d_order_ticket_user
     */
    private String convertLogicTableToPhysicalPrefix(String logicTableName) {
        // 根据实际业务调整映射规则
        if (logicTableName.equals("d_order")) {
            return "d_order";
        } else if (logicTableName.equals("d_order_ticket_user")) {
            return "d_order_ticket_user";
        } else if (logicTableName.equals("d_order_ticket_user_record")) {
            return "d_order_ticket_user_record";
        }
        
        // 默认：将 t_ 替换为 d_
        return logicTableName.replace("t_", "d_");
    }
}