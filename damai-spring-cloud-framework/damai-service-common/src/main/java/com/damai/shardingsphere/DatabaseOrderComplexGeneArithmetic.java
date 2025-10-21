package com.damai.shardingsphere;

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
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 订单分库，pro版本优化了分片算法，基于基因位计算分库，更加均匀和高效
 * @author: 阿星不是程序员
 **/
public class DatabaseOrderComplexGeneArithmetic implements ComplexKeysShardingAlgorithm<Long> {
    /**
     * 属性分库名
     * */
    private static final String SHARDING_COUNT_KEY_NAME = "sharding-count";
    
    /**
     * 属性分表名
     * */
    private static final String TABLE_SHARDING_COUNT_KEY_NAME = "table-sharding-count";
    
    /**
     * 分库数量
     * */
    private int shardingCount;
    
    /**
     * 分表数量
     * */
    private int tableShardingCount;
    
    @Override
    public void init(Properties props) {
        this.shardingCount = Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY_NAME));
        this.tableShardingCount = Integer.parseInt(props.getProperty(TABLE_SHARDING_COUNT_KEY_NAME));
    }
    
    /**
     * 此版本进行了优化，使得分库分表的数据更加均匀和高效，如果要看未优化版本，请查看大麦普通开源版本的DatabaseOrderComplexArithmetic类
     * */
    @Override
    public Collection<String> doSharding(Collection<String> allActualSplitDatabaseNames, 
                                         ComplexKeysShardingValue<Long> complexKeysShardingValue) {
        //返回的真实库名集合
        List<String> actualDatabaseNames = new ArrayList<>(allActualSplitDatabaseNames.size());
        //查询中的列名和值
        Map<String, Collection<Long>> columnNameAndShardingValuesMap = 
                complexKeysShardingValue.getColumnNameAndShardingValuesMap();
        //如果没有条件查询，那么就查所有的分表
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return allActualSplitDatabaseNames;
        }
        //order_number条件的值
        Collection<Long> orderNumberValues = columnNameAndShardingValuesMap.get("order_number");
        //user_id条件的值
        Collection<Long> userIdValues = columnNameAndShardingValuesMap.get("user_id");
        
        Long value = null;
        //如果是order_number查询
        if (CollectionUtil.isNotEmpty(orderNumberValues)) {
            value = orderNumberValues.stream().findFirst()
                    .orElseThrow(() -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
            //如果是user_id查询
        } else if (CollectionUtil.isNotEmpty(userIdValues)) {
            value = userIdValues.stream().findFirst()
                    .orElseThrow(() -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
        }
        //如果order_number或者user_id的值存在
        if (Objects.nonNull(value)) {
            //获得值后再获得实际的分库的索引
            long databaseIndex = calculateDatabaseIndex(shardingCount,value,tableShardingCount);
            String databaseIndexStr = String.valueOf(databaseIndex);
            for (String actualSplitDatabaseName : allActualSplitDatabaseNames) {
                //将所有的分库名和得到的分库索引进行匹配
                if (actualSplitDatabaseName.contains(databaseIndexStr)) {
                    actualDatabaseNames.add(actualSplitDatabaseName);
                    break;
                }
            }
            return actualDatabaseNames;
        }else {
            //如果没有分片键查询，则把所有真实库返回
            return allActualSplitDatabaseNames;
        }
    }
    
    /**
     * 计算给定表索引应分配到的数据库编号。
     * 核心思路：
     * - 分表使用ID的低位bit（最后 log2(tableCount) 位）
     * - 分库使用ID的中高位bit（跳过表基因位后的 log2(databaseCount) 位）
     * - 直接使用位运算，避免hashCode导致的分布不均
     *
     * @param databaseCount 数据库总数
     * @param splicingKey    分片键
     * @param tableCount    表总数
     * @return 分配到的数据库编号
     */
    public long calculateDatabaseIndex(Integer databaseCount, Long splicingKey, Integer tableCount) {
        // 计算表分片占用的bit位数
        long tableGeneLength = log2N(tableCount);
        
        // 将分片键右移tableGeneLength位，跳过表基因位
        // 然后与(databaseCount-1)进行按位与运算，得到库索引
        // 例如：ID=1101 (13), tableCount=4, databaseCount=4
        //   tableGeneLength=2, ID>>2=11 (3), (4-1)&3=3
        return (databaseCount - 1) & (splicingKey >> tableGeneLength);
    }
    
    public long log2N(long count) {
        return (long)(Math.log(count)/ Math.log(2));
    }
}
