package com.damai.shardingsphere;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.entity.Order;
import com.damai.entity.ShardingRouteMapping;
import com.damai.mapper.OrderMapper;
import com.damai.mapper.ShardingRouteMappingMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟分片迁移服务
 *
 * ⚠️ 重要约束：
 * 从8个物理分片扩展到16个物理分片时，必须一次性完成所有8张原表的拆分迁移。
 * 每张原表的后64个虚拟分片迁移到对应的新表。
 * 不能只拆分部分表，必须同时处理所有原表，以保证查询一致性。
 */
@Slf4j
@Service
public class VirtualShardMigrationService {
    
    @Autowired
    private VirtualShardingRouteManager routeManager;
    
    @Autowired
    private ShardingRouteMappingMapper routeMappingMapper;
    
    @Autowired
    private OrderMapper orderMapper;  // 需要注入订单Mapper
    
    /**
     * 迁移指定原表的后半部分数据到新表
     *
     * 注意：
     * 1. 订单表的id是雪花算法生成（自增但不连续）
     * 2. order_number是基因法生成（自增但不连续）
     * 3. 需要通过calculateLogicalShardId判断每条数据是否需要迁移
     *
     * @param sourceDatabase   源数据库名称
     * @param sourceTable      源表名称（如d_order_0）
     * @param targetDatabase   目标数据库名称
     * @param targetTable      目标表名称（如d_order_4）
     * @param startShardId     虚拟分片起始ID（如64）
     * @param endShardId       虚拟分片结束ID（如127）
     * @return 迁移的数据条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int migrateTableData(String sourceDatabase,
                                String sourceTable,
                                String targetDatabase,
                                String targetTable,
                                int startShardId,
                                int endShardId) {
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始迁移数据：{}.{} → {}.{}", sourceDatabase, sourceTable, targetDatabase, targetTable);
        log.info("虚拟分片范围：{}-{}", startShardId, endShardId);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // 步骤1：分批查询源表数据
        int pageSize = 1000;  // 每批处理1000条
        long lastId = 0;      // 使用id作为游标（雪花ID自增）
        int totalMigrated = 0;
        int batchCount = 0;
        
        while (true) {
            batchCount++;
            
            // 使用 HintManager 强制路由到指定的物理表（绕过分片逻辑）
            List<Order> orders;
            try (HintManager hintManager = HintManager.getInstance()) {
                // 步骤1：强制指定数据源（如 damai_order_0）
                // 从完整库名中提取后缀（damai_order_0 → 0）
                String dbSuffix = extractDatabaseSuffix(sourceDatabase);
                hintManager.addDatabaseShardingValue("d_order", dbSuffix);  // 逻辑表名, 库后缀
                
                // 步骤2：强制指定表（如 d_order_0）
                // 从完整表名中提取后缀（d_order_0 → 0）
                String tableSuffix = extractTableSuffix(sourceTable);
                hintManager.addTableShardingValue("d_order", tableSuffix);  // 逻辑表名, 表后缀
                
                // 步骤3：执行查询（此时不会走自动分片路由，而是直接查询指定的物理表）
                orders = orderMapper.selectList(
                        Wrappers.lambdaQuery(Order.class)
                                .gt(Order::getId, lastId)  // id > lastId
                                .orderByAsc(Order::getId)   // 按id升序
                                .last("LIMIT " + pageSize)  // 限制条数
                );
            }  // try-with-resources 自动关闭 HintManager，清理 ThreadLocal
            
            if (orders.isEmpty()) {
                log.info("✓ 源表数据查询完毕");
                break;
            }
            
            // 步骤2：过滤需要迁移的数据
            List<Order> toMigrate = new ArrayList<>();
            for (Order order : orders) {
                // 计算该订单对应的虚拟分片ID
                int logicalShardId = routeManager.calculateLogicalShardId(order.getOrderNumber());
                
                // 判断是否在目标虚拟分片范围内
                if (logicalShardId >= startShardId && logicalShardId <= endShardId) {
                    toMigrate.add(order);
                }
            }
            
            // 步骤3：批量插入到目标表
            if (!toMigrate.isEmpty()) {
                // 使用 HintManager 强制路由到目标物理表
                try (HintManager hintManager = HintManager.getInstance()) {
                    // 强制指定目标数据源和表
                    String targetDbSuffix = extractDatabaseSuffix(targetDatabase);
                    String targetTableSuffix = extractTableSuffix(targetTable);
                    
                    hintManager.addDatabaseShardingValue("d_order", targetDbSuffix);
                    hintManager.addTableShardingValue("d_order", targetTableSuffix);
                    
                    // 批量插入（实际应该使用批量插入优化）
                    for (Order order : toMigrate) {
                        orderMapper.insert(order);
                    }
                }
                
                totalMigrated += toMigrate.size();
                log.info("第{}批：查询{}条，迁移{}条，累计迁移{}条",
                        batchCount, orders.size(), toMigrate.size(), totalMigrated);
            } else {
                log.debug("第{}批：查询{}条，无需迁移", batchCount, orders.size());
            }
            
            // 步骤4：更新游标
            lastId = orders.get(orders.size() - 1).getId();
            
            // 步骤5：防止过快，可选的休眠
            if (batchCount % 10 == 0) {
                try {
                    Thread.sleep(100);  // 每10批休眠100ms，降低数据库压力
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ 数据迁移完成：共迁移 {} 条数据", totalMigrated);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return totalMigrated;
    }
    
    /**
     * 完整的迁移流程（包含数据迁移和路由表更新）
     *
     * @param startShardId 虚拟分片起始ID
     * @param endShardId 虚拟分片结束ID
     * @param sourceDatabase 源数据库
     * @param sourceTable 源表
     * @param targetDatabase 目标数据库
     * @param targetTable 目标表
     * @return 迁移的虚拟分片数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int migrateVirtualShardRange(int startShardId,
                                        int endShardId,
                                        String sourceDatabase,
                                        String sourceTable,
                                        String targetDatabase,
                                        String targetTable) {
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始迁移虚拟分片范围：{}-{}", startShardId, endShardId);
        log.info("源库表：{}.{}", sourceDatabase, sourceTable);
        log.info("目标库表：{}.{}", targetDatabase, targetTable);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // 步骤1：执行数据迁移
        int migratedCount = migrateTableData(sourceDatabase, sourceTable,
                targetDatabase, targetTable,
                startShardId, endShardId);
        
        log.info("✓ 数据迁移完成：迁移了 {} 条数据", migratedCount);
        
        // 步骤2：批量更新路由表
        // 从表名中提取表后缀（如 d_order_4 → 4）
        int targetTableSuffix = extractTableSuffixInt(targetTable);
        
        int updatedCount = routeMappingMapper.update(
                null,
                Wrappers.lambdaUpdate(ShardingRouteMapping.class)
                        .set(ShardingRouteMapping::getPhysicalDatabaseSuffix, getDatabaseSuffix(targetDatabase))
                        .set(ShardingRouteMapping::getPhysicalTableSuffix, targetTableSuffix)
                        .setSql("version = version + 1")
                        .ge(ShardingRouteMapping::getLogicalShardId, startShardId)
                        .le(ShardingRouteMapping::getLogicalShardId, endShardId)
        );
        
        log.info("✓ 路由表更新完成：更新了 {} 条记录", updatedCount);
        
        // 步骤3：刷新缓存
        routeManager.reloadRouteMapping();
        log.info("✓ 路由缓存刷新完成");
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ 虚拟分片范围 {}-{} 迁移完成！", startShardId, endShardId);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return updatedCount;
    }
    
    /**
     * 回滚迁移（秒级回滚）
     *
     * @param startShardId 虚拟分片起始ID
     * @param endShardId 虚拟分片结束ID
     * @param originalDatabase 原始数据库
     * @param originalTable 原始表
     * @return 回滚的虚拟分片数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int rollbackMigration(int startShardId,
                                 int endShardId,
                                 String originalDatabase,
                                 String originalTable) {
        
        log.warn("⚠️ 开始回滚虚拟分片 {}-{} 到原表 {}.{}",
                startShardId, endShardId, originalDatabase, originalTable);
        
        // 从表名中提取表后缀
        int originalTableSuffix = extractTableSuffixInt(originalTable);
        
        // 更新路由表，恢复到原始映射
        int updatedCount = routeMappingMapper.update(
                null,
                Wrappers.lambdaUpdate(ShardingRouteMapping.class)
                        .set(ShardingRouteMapping::getPhysicalDatabaseSuffix, getDatabaseSuffix(originalDatabase))
                        .set(ShardingRouteMapping::getPhysicalTableSuffix, originalTableSuffix)
                        .setSql("version = version + 1")
                        .ge(ShardingRouteMapping::getLogicalShardId, startShardId)
                        .le(ShardingRouteMapping::getLogicalShardId, endShardId)
        );
        
        // 刷新缓存
        routeManager.reloadRouteMapping();
        
        log.info("✅ 回滚完成，虚拟分片 {}-{} 已恢复到 {}.{}",
                startShardId, endShardId, originalDatabase, originalTable);
        
        return updatedCount;
    }
    
    /**
     * 清理源表中已迁移的数据
     *
     * @param sourceDatabase 源数据库名称
     * @param sourceTable    源表名称
     * @param startShardId   虚拟分片起始ID
     * @param endShardId     虚拟分片结束ID
     * @return 删除的数据条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupSourceTableData(String sourceDatabase,
                                      String sourceTable,
                                      int startShardId,
                                      int endShardId) {
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始清理源表数据：{}.{}", sourceDatabase, sourceTable);
        log.info("虚拟分片范围：{}-{}", startShardId, endShardId);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        int pageSize = 1000;
        long lastId = 0;
        int totalDeleted = 0;
        int batchCount = 0;
        
        while (true) {
            batchCount++;
            
            // 查询源表数据
            List<Order> orders;
            try (HintManager hintManager = HintManager.getInstance()) {
                String dbSuffix = extractDatabaseSuffix(sourceDatabase);
                String tableSuffix = extractTableSuffix(sourceTable);
                
                hintManager.addDatabaseShardingValue("d_order", dbSuffix);
                hintManager.addTableShardingValue("d_order", tableSuffix);
                
                orders = orderMapper.selectList(
                        Wrappers.lambdaQuery(Order.class)
                                .gt(Order::getId, lastId)
                                .orderByAsc(Order::getId)
                                .last("LIMIT " + pageSize)
                );
            }
            
            if (orders.isEmpty()) {
                log.info("✓ 源表数据清理完毕");
                break;
            }
            
            // 过滤需要删除的数据（只删除已迁移的数据）
            List<Long> toDeleteIds = new ArrayList<>();
            for (Order order : orders) {
                int logicalShardId = routeManager.calculateLogicalShardId(order.getOrderNumber());
                
                if (logicalShardId >= startShardId && logicalShardId <= endShardId) {
                    toDeleteIds.add(order.getId());
                }
            }
            
            // 批量删除
            if (!toDeleteIds.isEmpty()) {
                try (HintManager hintManager = HintManager.getInstance()) {
                    String dbSuffix = extractDatabaseSuffix(sourceDatabase);
                    String tableSuffix = extractTableSuffix(sourceTable);
                    
                    hintManager.addDatabaseShardingValue("d_order", dbSuffix);
                    hintManager.addTableShardingValue("d_order", tableSuffix);
                    
                    // 批量删除（分批进行，避免一次删除过多）
                    int deleteCount = orderMapper.deleteBatchIds(toDeleteIds);
                    totalDeleted += deleteCount;
                    
                    log.info("第{}批：查询{}条，删除{}条，累计删除{}条",
                            batchCount, orders.size(), deleteCount, totalDeleted);
                }
            } else {
                log.debug("第{}批：查询{}条，无需删除", batchCount, orders.size());
            }
            
            // 更新游标
            lastId = orders.get(orders.size() - 1).getId();
            
            // 防止过快
            if (batchCount % 10 == 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ 源表数据清理完成：共删除 {} 条数据", totalDeleted);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return totalDeleted;
    }
    
    /**
     * 从表名中提取表后缀
     * @param tableName 完整表名（如 d_order_4、d_order_ticket_user_7）
     * @return 表后缀字符串（如 "4"、"7"）- 注意返回字符串，因为 HintManager 需要
     */
    private String extractTableSuffix(String tableName) {
        // 获取最后一个下划线后的数字
        int lastUnderscoreIndex = tableName.lastIndexOf('_');
        if (lastUnderscoreIndex == -1) {
            throw new IllegalArgumentException("无效的表名格式：" + tableName);
        }
        
        return tableName.substring(lastUnderscoreIndex + 1);
    }
    
    /**
     * 从库名中提取库后缀
     * @param databaseName 完整库名（如 damai_order_0、damai_order_1）
     * @return 库后缀字符串（如 "0"、"1"）- 注意返回字符串，因为 HintManager 需要
     */
    private String extractDatabaseSuffix(String databaseName) {
        int lastUnderscoreIndex = databaseName.lastIndexOf('_');
        if (lastUnderscoreIndex == -1) {
            throw new IllegalArgumentException("无效的库名格式：" + databaseName);
        }
        
        return databaseName.substring(lastUnderscoreIndex + 1);
    }
    
    /**
     * 从表名中提取表后缀（Integer版本，用于路由表更新）
     * @param tableName 完整表名
     * @return 表后缀数字
     */
    private int extractTableSuffixInt(String tableName) {
        String suffix = extractTableSuffix(tableName);
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的表后缀：" + suffix, e);
        }
    }
    
    /**
     * 从库名中获取后缀（用于路由表更新）
     * @param databaseName 完整库名（如 damai_order_0）
     * @return 库后缀字符串（如 "0"）
     */
    private String getDatabaseSuffix(String databaseName) {
        return extractDatabaseSuffix(databaseName);
    }
}