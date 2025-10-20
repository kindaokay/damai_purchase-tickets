package com.damai.shardingsphere.powerof2;

import cn.hutool.core.collection.CollectionUtil;
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
 * 幂次方扩容的分表算法
 * 核心：表数量必须是2的幂次方，扩容时翻倍
 */
public class TableOrderPowerOf2Algorithm implements ComplexKeysShardingAlgorithm<Long> {
    
    /**
     * 属性：分表名
     */
    private static final String SHARDING_COUNT_KEY_NAME = "sharding-count";
    
    /**
     * 用于计算表索引的掩码
     */
    private long tableMask;
    
    @Override
    public void init(Properties props) {
        //当前表数量（必须是2的幂次方）
        final int tableCount = Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY_NAME));
        
        // 验证是否为2的幂次方
        if (!isPowerOf2(tableCount)) {
            throw new IllegalArgumentException(
                String.format("表数量必须是2的幂次方（2,4,8,16...），当前: %d", tableCount)
            );
        }
        
        // 计算掩码：tableCount=8时，mask=7=0b111
        this.tableMask = tableCount - 1;
    }
    
    @Override
    public Collection<String> doSharding(
        Collection<String> allActualSplitTableNames,
        ComplexKeysShardingValue<Long> complexKeysShardingValue) {
        
        List<String> actualTableNames = new ArrayList<>();
        String logicTableName = complexKeysShardingValue.getLogicTableName();
        Map<String, Collection<Long>> columnNameAndShardingValuesMap = 
            complexKeysShardingValue.getColumnNameAndShardingValuesMap();
        
        // 如果没有条件查询，返回空（不允许全表扫描）
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return actualTableNames;
        }
        
        // 获取分片键
        Long shardingKey = getShardingKey(columnNameAndShardingValuesMap);
        
        if (Objects.nonNull(shardingKey)) {
            // ⭐ 核心：使用位运算计算表索引
            long tableIndex = shardingKey & tableMask;
            actualTableNames.add(logicTableName + "_" + tableIndex);
            return actualTableNames;
        }
        
        // 没有分片键，返回所有表（允许特殊场景）
        return allActualSplitTableNames;
    }
    
    /**
     * 获取分片键（优先order_number，其次user_id）
     */
    private Long getShardingKey(Map<String, Collection<Long>> columnMap) {
        //order_number条件的值
        Collection<Long> orderNumberValues = columnMap.get("order_number");
        //user_id条件的值
        Collection<Long> userIdValues = columnMap.get("user_id");
        //如果是order_number查询
        if (CollectionUtil.isNotEmpty(orderNumberValues)) {
            return orderNumberValues.stream().findFirst()
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
        } else if (CollectionUtil.isNotEmpty(userIdValues)) {
            //如果是user_id查询
            return userIdValues.stream().findFirst()
                .orElseThrow(() -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
        }
        return null;
    }
    
    /**
     * 判断是否为2的幂次方
     */
    private boolean isPowerOf2(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}