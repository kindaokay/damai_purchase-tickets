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
 * 幂次方扩容的分库算法
 */
public class DatabaseOrderPowerOf2Algorithm implements ComplexKeysShardingAlgorithm<Long> {
    
    /**
     * 属性分库名
     * */
    private static final String SHARDING_COUNT_KEY_NAME = "sharding-count";
    
    /**
     * 属性分表名
     * */
    private static final String TABLE_SHARDING_COUNT_KEY_NAME = "table-sharding-count";
    
    /**
     * 表基因位数（用于跳过）
     */
    private int tableBitCount;
    
    /**
     * 库掩码
     */
    private long databaseMask;
    
    @Override
    public void init(Properties props) {
        //分库数量
        final int databaseCount = Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY_NAME));
        //分表数量
        final int tableCount = Integer.parseInt(props.getProperty(TABLE_SHARDING_COUNT_KEY_NAME));
        // 验证是否为2的幂次方
        if (!isPowerOf2(databaseCount) || !isPowerOf2(tableCount)) {
            throw new IllegalArgumentException(
                String.format("库数和表数必须是2的幂次方，当前：库=%d, 表=%d", databaseCount, tableCount)
            );
        }
        // 计算表基因位数（用于跳过）
        this.tableBitCount = Integer.numberOfTrailingZeros(tableCount);
        
        // 计算库掩码
        this.databaseMask = databaseCount - 1;
    }
    
    @Override
    public Collection<String> doSharding(
        Collection<String> allActualSplitDatabaseNames,
        ComplexKeysShardingValue<Long> complexKeysShardingValue) {
        
        List<String> actualDatabaseNames = new ArrayList<>();
        Map<String, Collection<Long>> columnNameAndShardingValuesMap = 
            complexKeysShardingValue.getColumnNameAndShardingValuesMap();
        
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return actualDatabaseNames;
        }
        
        Long shardingKey = getShardingKey(columnNameAndShardingValuesMap);
        
        if (Objects.nonNull(shardingKey)) {
            // ⭐ 核心：跳过表基因位，提取库基因
            long databaseIndex = (shardingKey >> tableBitCount) & databaseMask;
            String databaseIndexStr = String.valueOf(databaseIndex);
            for (String actualSplitDatabaseName : allActualSplitDatabaseNames) {
                //将所有的分库名和得到的分库索引进行匹配
                if (actualSplitDatabaseName.contains(databaseIndexStr)) {
                    actualDatabaseNames.add(actualSplitDatabaseName);
                    break;
                }
            }
            return actualDatabaseNames;
        }
        return allActualSplitDatabaseNames;
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
    
    private boolean isPowerOf2(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    

}