package com.damai.shardingsphere.powersoftwo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.entity.Order;
import com.damai.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 幂次方扩容迁移服务
 * 
 * 使用场景：从 2库×4表 扩容到 2库×8表
 */
@Service
@Slf4j
public class PowerOf2ScaleOutService {
    
    @Autowired
    private OrderMapper orderMapper;
    
    /**
     * 执行完整扩容流程（2库×4表 → 2库×8表）
     */
    public void executeScaleOut() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始幂次方扩容：2库×4表 → 2库×8表");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        int oldTableCount = 4;
        int newTableCount = 8;
        
        // 验证参数
        validateScaleOut(oldTableCount, newTableCount);
        
        // 步骤1：迁移damai_order_0库
        scaleOutDatabase("damai_order_0", oldTableCount, newTableCount);
        
        // 步骤2：迁移damai_order_1库
        scaleOutDatabase("damai_order_1", oldTableCount, newTableCount);
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ 幂次方扩容完成！");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    /**
     * 验证扩容参数
     */
    private void validateScaleOut(int oldCount, int newCount) {
        if (newCount != oldCount * 2) {
            throw new IllegalArgumentException(
                String.format("新表数量必须是旧表数量的2倍，当前：%d → %d", oldCount, newCount)
            );
        }
        
        if (!isPowerOf2(oldCount) || !isPowerOf2(newCount)) {
            throw new IllegalArgumentException(
                String.format("表数量必须是2的幂次方，当前：%d → %d", oldCount, newCount)
            );
        }
        
        log.info("✓ 参数验证通过：{} → {}", oldCount, newCount);
    }
    
    
    /**
     * 扩容单个数据库
     */
    private void scaleOutDatabase(String database, int oldTableCount, int newTableCount) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始扩容数据库：{}", database);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // 计算新增位掩码
        int oldBitCount = Integer.numberOfTrailingZeros(oldTableCount);
        // 新增的那一位（用于判断数据是否需要迁移）
        long newBitMask = 1L << oldBitCount;  
        
        // 拆分每张原表
        for (int oldTableIndex = 0; oldTableIndex < oldTableCount; oldTableIndex++) {
            int newTableIndex = oldTableIndex + oldTableCount;
            splitTable(database, oldTableIndex, newTableIndex, newBitMask, newTableCount);
        }
        
        log.info("✅ 数据库 {} 扩容完成", database);
    }
    
    /**
     * 拆分单张表
     * 
     * @param database 数据库名称
     * @param oldTableIndex 原表索引（如0）
     * @param newTableIndex 新表索引（如4）
     * @param newBitMask 新增位掩码（如0b100）
     * @param newTableCount 新的表数量（8）
     */
    private void splitTable(String database, int oldTableIndex, 
                            int newTableIndex, long newBitMask, int newTableCount) {
        log.info("开始拆分：{}.d_order_{} → d_order_{} + d_order_{}",
            database, oldTableIndex, oldTableIndex, newTableIndex);
        
        int pageSize = 1000;
        long lastId = 0;
        long totalMigrated = 0;
        int batchCount = 0;
        
        while (true) {
            batchCount++;
            
            // 步骤1：查询原表数据
            List<Order> orders = queryOrders(database, oldTableIndex, lastId, pageSize);
            
            if (orders.isEmpty()) {
                log.info("✓ 原表数据遍历完毕");
                break;
            }
            
            // 步骤2：按新位分组
            // 新位=0的数据留在原表，新位=1的数据迁移到新表
            List<Order> toMigrate = orders.stream()
                .filter(order -> (order.getOrderNumber() & newBitMask) != 0)
                .collect(Collectors.toList());
            
            // 步骤3：迁移数据
            if (!toMigrate.isEmpty()) {
                // ⭐ 使用ShardingSphere的HintManager进行精确路由
                insertOrders(database, newTableIndex, toMigrate);
                deleteOrders(database, oldTableIndex, toMigrate);
                
                totalMigrated += toMigrate.size();
                log.info("第{}批：查询{}条，迁移{}条，累计{}条",
                    batchCount, orders.size(), toMigrate.size(), totalMigrated);
            } else {
                log.info("第{}批：查询{}条，无需迁移", batchCount, orders.size());
            }
            
            // 步骤4：更新游标
            lastId = orders.get(orders.size() - 1).getId();
            
            // 步骤5：控制速度
            if (batchCount % 10 == 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        log.info("✅ 拆分完成：d_order_{} → 迁移{}条到 d_order_{}",
            oldTableIndex, totalMigrated, newTableIndex);
    }
    
    /**
     * 查询订单数据
     */
    private List<Order> queryOrders(String database, int tableIndex, 
                                    long lastId, int limit) {
        try (HintManager hintManager = HintManager.getInstance()) {
            // 强制路由到指定库表
            String dbSuffix = extractDatabaseSuffix(database);
            hintManager.addDatabaseShardingValue("d_order", dbSuffix);
            hintManager.addTableShardingValue("d_order", String.valueOf(tableIndex));
            
            // 执行查询
            return orderMapper.selectList(
                Wrappers.lambdaQuery(Order.class)
                    .gt(Order::getId, lastId)
                    .orderByAsc(Order::getId)
                    .last("LIMIT " + limit)
            );
        }
    }
    
    /**
     * 插入订单到新表（使用ShardingSphere的HintManager）
     */
    private void insertOrders(String database, int tableIndex, List<Order> orders) {
        if (orders.isEmpty()) {
            return;
        }
        
        try (HintManager hintManager = HintManager.getInstance()) {
            String dbSuffix = extractDatabaseSuffix(database);
            hintManager.addDatabaseShardingValue("d_order", dbSuffix);
            hintManager.addTableShardingValue("d_order", String.valueOf(tableIndex));
            
            for (Order order : orders) {
                orderMapper.insert(order);
            }
            
            log.info("  → 插入{}条数据到 {}.d_order_{}", orders.size(), database, tableIndex);
        }
    }
    
    /**
     * 从原表删除数据（使用ShardingSphere的HintManager）
     */
    private void deleteOrders(String database, int tableIndex, List<Order> orders) {
        if (orders.isEmpty()) {
            return;
        }
        
        try (HintManager hintManager = HintManager.getInstance()) {
            String dbSuffix = extractDatabaseSuffix(database);
            hintManager.addDatabaseShardingValue("d_order", dbSuffix);
            hintManager.addTableShardingValue("d_order", String.valueOf(tableIndex));
            
            List<Long> ids = orders.stream()
                .map(Order::getId)
                .collect(Collectors.toList());
            
            orderMapper.delete(
                Wrappers.lambdaQuery(Order.class).in(Order::getId, ids)
            );
            
            log.info("  → 从 {}.d_order_{} 删除{}条数据", database, tableIndex, ids.size());
        }
    }
    
    
    /**
     * 提取数据库后缀
     */
    private String extractDatabaseSuffix(String database) {
        int lastUnderscoreIndex = database.lastIndexOf('_');
        return database.substring(lastUnderscoreIndex + 1);
    }
    
    /**
     * 判断是否为2的幂次方
     */
    private boolean isPowerOf2(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}