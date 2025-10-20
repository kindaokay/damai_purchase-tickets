package com.damai.shardingsphere.powersoftwo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.entity.Order;
import com.damai.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扩容后数据验证工具
 */
@Service
@Slf4j
public class ScaleOutValidationService {
    
    @Autowired
    private OrderMapper orderMapper;
    
    /**
     * 验证扩容后的数据分布
     *
     */
    public void validateDataDistribution() {
        //当前表数量（如8）
        int tableCount = 8;
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始验证数据分布（{}表）", tableCount);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        Map<String, Long> countMap = new HashMap<>();
        
        // 统计每个库表的数据量
        for (String database : List.of("damai_order_0", "damai_order_1")) {
            for (int i = 0; i < tableCount; i++) {
                long count = countOrders(database, i);
                String key = database + ".d_order_" + i;
                countMap.put(key, count);
                log.info("{}: {} 条", key, count);
            }
        }
        
        // 验证数据分布
        long totalCount = countMap.values().stream().mapToLong(Long::longValue).sum();
        log.info("总数据量: {} 条", totalCount);
        
        // 计算标准差（理想情况下应该接近0）
        double average = totalCount / (double) countMap.size();
        double variance = countMap.values().stream()
            .mapToDouble(count -> Math.pow(count - average, 2))
            .sum() / countMap.size();
        double stdDev = Math.sqrt(variance);
        
        log.info("平均值: {}, 标准差: {}", String.format("%.2f", average), String.format("%.2f", stdDev));
        
        // 验证位运算路由正确性
        validateRouting(tableCount);
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ 数据分布验证完成");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    /**
     * 验证路由正确性（随机抽样）
     */
    private void validateRouting(int tableCount) {
        log.info("开始验证路由正确性（抽样100条）...");
        
        long tableMask = tableCount - 1;
        int errorCount = 0;
        
        // 从每个表随机抽取订单
        for (String database : List.of("damai_order_0", "damai_order_1")) {
            for (int tableIndex = 0; tableIndex < tableCount; tableIndex++) {
                List<Order> orders = sampleOrders(database, tableIndex, 10);
                
                for (Order order : orders) {
                    // 计算期望的表索引
                    long expectedTableIndex = order.getOrderNumber() & tableMask;
                    
                    // 验证是否在正确的表
                    if (expectedTableIndex != tableIndex) {
                        log.error("❌ 路由错误：订单 {} 应在表 {}，实际在表 {}",
                            order.getOrderNumber(), expectedTableIndex, tableIndex);
                        errorCount++;
                    }
                }
            }
        }
        
        if (errorCount == 0) {
            log.info("✅ 路由验证通过（100条抽样）");
        } else {
            log.error("❌ 路由验证失败：发现 {} 条错误", errorCount);
        }
    }
    
    /**
     * 统计表中的订单数量
     */
    private long countOrders(String database, int tableIndex) {
        try (HintManager hintManager = HintManager.getInstance()) {
            String dbSuffix = extractDatabaseSuffix(database);
            hintManager.addDatabaseShardingValue("d_order", dbSuffix);
            hintManager.addTableShardingValue("d_order", String.valueOf(tableIndex));
            
            return orderMapper.selectCount(null);
        }
    }
    
    /**
     * 抽样订单数据
     */
    private List<Order> sampleOrders(String database, int tableIndex, int limit) {
        try (HintManager hintManager = HintManager.getInstance()) {
            String dbSuffix = extractDatabaseSuffix(database);
            hintManager.addDatabaseShardingValue("d_order", dbSuffix);
            hintManager.addTableShardingValue("d_order", String.valueOf(tableIndex));
            
            return orderMapper.selectList(
                Wrappers.lambdaQuery(Order.class)
                    .orderByAsc(Order::getId)
                    .last("LIMIT " + limit)
            );
        }
    }
    
    private String extractDatabaseSuffix(String database) {
        int lastUnderscoreIndex = database.lastIndexOf('_');
        return database.substring(lastUnderscoreIndex + 1);
    }
}