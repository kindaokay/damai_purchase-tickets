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
 * 基于虚拟分片的分库算法
 * 适用于所有按order_number、user_id分片的表
 */
public class DatabaseOrderVirtualShardingAlgorithm implements ComplexKeysShardingAlgorithm<Long> {
    
    @Override
    public void init(Properties props) {
        // 无需初始化
    }
    
    @Override
    public Collection<String> doSharding(Collection<String> allActualSplitDatabaseNames, 
                                         ComplexKeysShardingValue<Long> complexKeysShardingValue) {
        //返回的真实库名集合
        List<String> actualDatabaseNames = new ArrayList<>();
        //查询中的列名和值
        Map<String, Collection<Long>> columnNameAndShardingValuesMap = 
                complexKeysShardingValue.getColumnNameAndShardingValuesMap();
        
        // 无条件查询 → 返回所有库
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return allActualSplitDatabaseNames;
        }
        
        // 获取分片键
        
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
        //如果order_number或者user_id的值存在
        if (Objects.nonNull(shardingKey)) {
            //通过路由管理器获取物理分片
            //这里通过SpringUtil的工具类获取是因为ShardingSphere相关的类无法通过Spring注入
            PhysicalShard physicalShard = SpringUtil.getBean(VirtualShardingRouteManager.class)
                    .route(shardingKey);
            
            if (Objects.nonNull(physicalShard)) {
                // 返回目标数据库
                String targetDatabase = physicalShard.getDatasourceName();
                for (String actualSplitDatabaseName : allActualSplitDatabaseNames) {
                    //将所有的分库名和得到的分库索引进行匹配
                    if (actualSplitDatabaseName.contains(targetDatabase)) {
                        actualDatabaseNames.add(actualSplitDatabaseName);
                        break;
                    }
                }
            }
            return actualDatabaseNames.isEmpty() ? allActualSplitDatabaseNames : actualDatabaseNames;
        }
        return allActualSplitDatabaseNames;
    }
}