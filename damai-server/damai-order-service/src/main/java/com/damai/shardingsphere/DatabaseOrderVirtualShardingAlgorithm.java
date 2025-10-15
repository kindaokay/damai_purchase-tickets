package com.damai.shardingsphere;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.core.SpringUtil;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * 基于虚拟分片的分库算法
 * 适用于所有按order_number、user_id分片的表
 */
public class DatabaseOrderVirtualShardingAlgorithm implements ComplexKeysShardingAlgorithm<Long> {
    
    @Override
    public void init(Properties props) {
        // 无需初始化
    }
    
    @Override
    public Collection<String> doSharding(
        Collection<String> allActualSplitDatabaseNames, 
        ComplexKeysShardingValue<Long> complexKeysShardingValue) {
        
        List<String> actualDatabaseNames = new ArrayList<>();
        Map<String, Collection<Long>> columnNameAndShardingValuesMap = 
            complexKeysShardingValue.getColumnNameAndShardingValuesMap();
        
        // 无条件查询 → 返回所有库
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return allActualSplitDatabaseNames;
        }
        
        // 获取分片键
        Collection<Long> orderNumberValues = columnNameAndShardingValuesMap.get("order_number");
        Collection<Long> userIdValues = columnNameAndShardingValuesMap.get("user_id");
        
        Long shardingKey = null;
        if (CollectionUtil.isNotEmpty(orderNumberValues)) {
            shardingKey = orderNumberValues.stream().findFirst()
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
        } else if (CollectionUtil.isNotEmpty(userIdValues)) {
            shardingKey = userIdValues.stream().findFirst()
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
        }
        
        if (Objects.nonNull(shardingKey)) {
            // 通过路由管理器获取物理分片
            VirtualShardingRouteManager.PhysicalShard physicalShard = 
                    SpringUtil.getBean(VirtualShardingRouteManager.class).route(shardingKey);
            
            if (physicalShard != null) {
                // 返回目标数据库
                String targetDatabase = physicalShard.getDatasourceName();
                for (String databaseName : allActualSplitDatabaseNames) {
                    if (databaseName.contains(targetDatabase)) {
                        actualDatabaseNames.add(databaseName);
                        break;
                    }
                }
            }
            
            return actualDatabaseNames.isEmpty() ? allActualSplitDatabaseNames : actualDatabaseNames;
        }
        
        return allActualSplitDatabaseNames;
    }
}