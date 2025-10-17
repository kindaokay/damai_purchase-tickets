package com.damai.shardingsphere;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.client.OrderClient;
import com.damai.entity.Order;
import com.damai.entity.ShardingRouteMapping;
import com.damai.mapper.OrderMapper;
import com.damai.mapper.ShardingRouteMappingMapper;
import com.damai.shardingsphere.virtualsharding.VirtualShardingAlgorithmFunc;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 虚拟分片迁移服务
 *
 * ⚠️ 重要约束：
 * 从8个物理分片扩展到16个物理分片时，必须一次性完成所有8张原表的拆分迁移。
 * 
 * ⚠️ 核心修复：迁移策略基于用户维度
 * 问题：订单号虽然融合了用户ID的基因位，但整体值不同，导致 orderNumber % 128 和 userId % 128 结果不同
 * 如果按订单号的虚拟分片ID判断迁移，可能导致同一用户的订单分散到不同表
 * 
 * 解决方案：
 * 1. 迁移判断基于用户ID的虚拟分片ID（而不是订单号）
 * 2. 同一用户的所有订单保持在同一张表
 * 3. 迁移后更新所有相关订单号的路由映射
 */
@Slf4j
@Service
public class VirtualShardMigrationTask {
    
    @Autowired
    private ShardingRouteMappingMapper shardingRouteMappingMapper;
    
    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderClient orderClient;
    
    /**
     * 迁移指定原表的后半部分数据到新表
     * 
     * ⚠️ 核心修改：基于用户ID的虚拟分片ID判断是否迁移（而不是订单号）
     * 
     * 注意：
     * 1. 订单表的id是雪花算法生成（自增但不连续）
     * 2. order_number是基因法生成（自增但不连续）
     * 3. ⚠️ 必须基于用户ID判断迁移，保证同一用户的所有订单在同一张表
     *
     * @param sourceDatabase   源数据库名称
     * @param sourceTable      源表名称（如d_order_0）
     * @param targetDatabase   目标数据库名称
     * @param targetTable      目标表名称（如d_order_4）
     * @param startShardId     虚拟分片起始ID（基于用户ID，如64）
     * @param endShardId       虚拟分片结束ID（基于用户ID，如127）
     * @return 迁移结果（包含迁移的数据条数和需要更新路由的订单号虚拟分片ID集合）
     */
    @Transactional(rollbackFor = Exception.class)
    public MigrationResult migrateTableData(String sourceDatabase, String sourceTable,
                                            String targetDatabase, String targetTable,
                                            int startShardId, int endShardId) {
        log.info("开始迁移数据：{}.{} → {}.{}", sourceDatabase, sourceTable, targetDatabase, targetTable);
        log.info("虚拟分片范围（基于用户ID）：{}-{}", startShardId, endShardId);
        
        // 每批1000条
        int pageSize = 1000;
        // 游标（基于雪花ID自增）
        long lastId = 0;
        // 累计迁移数量
        int totalMigrated = 0;
        // 批次计数
        int batchCount = 0;
        // 记录需要更新路由映射的订单号虚拟分片ID集合
        Set<Integer> orderLogicalShardIds = new HashSet<>();
        
        while (true) {
            batchCount++;
            
            // ═══════════════════════════════════════════════════════
            // 第1步：分批查询源表数据（使用 HintManager 强制路由）
            // ═══════════════════════════════════════════════════════
            List<Order> orders;
            try (HintManager hintManager = HintManager.getInstance()) {
                // 1.1 强制指定数据源（如 damai_order_0）
                String dbSuffix = extractDatabaseSuffix(sourceDatabase);
                hintManager.addDatabaseShardingValue("d_order", dbSuffix);
                
                // 1.2 强制指定表（如 d_order_0）
                String tableSuffix = extractTableSuffix(sourceTable);
                hintManager.addTableShardingValue("d_order", tableSuffix);
                
                // 1.3 执行查询（直接查询指定物理表）
                orders = orderMapper.selectList(
                        Wrappers.lambdaQuery(Order.class)
                                // id > lastId
                                .gt(Order::getId, lastId)
                                // 按id升序
                                .orderByAsc(Order::getId)
                                // 限制条数
                                .last("LIMIT " + pageSize)
                );
            }  // 自动关闭 HintManager，清理 ThreadLocal
            
            // 查询完毕，退出循环
            if (orders.isEmpty()) {
                log.info("✓ 源表数据查询完毕");
                break;
            }
            
            // ═══════════════════════════════════════════════════════
            // 第2步：过滤需要迁移的数据
            // ⚠️ 基于用户ID的虚拟分片ID判断
            // ═══════════════════════════════════════════════════════
            List<Order> toMigrate = new ArrayList<>();
            for (Order order : orders) {
                int userLogicalShardId = VirtualShardingAlgorithmFunc
                        .calculateLogicalShardId(order.getUserId());
                
                // 判断用户ID的虚拟分片是否在迁移范围内
                if (userLogicalShardId >= startShardId && userLogicalShardId <= endShardId) {
                    toMigrate.add(order);
                    
                    // 记录该订单号的虚拟分片ID，后续需要更新其路由映射
                    int orderLogicalShardId = VirtualShardingAlgorithmFunc
                            .calculateLogicalShardId(order.getOrderNumber());
                    orderLogicalShardIds.add(orderLogicalShardId);
                }
            }
            
            // ═══════════════════════════════════════════════════════
            // 第3步：批量插入到目标表（使用 HintManager 强制路由）
            // ═══════════════════════════════════════════════════════
            if (!toMigrate.isEmpty()) {
                try (HintManager hintManager = HintManager.getInstance()) {
                    // 3.1 强制指定目标数据源和表
                    String targetDbSuffix = extractDatabaseSuffix(targetDatabase);
                    String targetTableSuffix = extractTableSuffix(targetTable);
                    
                    hintManager.addDatabaseShardingValue("d_order", targetDbSuffix);
                    hintManager.addTableShardingValue("d_order", targetTableSuffix);
                    
                    // 3.2 批量插入
                    for (Order order : toMigrate) {
                        orderMapper.insert(order);
                    }
                }
                
                totalMigrated += toMigrate.size();
                log.info("第{}批：查询{}条，迁移{}条（基于用户ID判断），累计迁移{}条",
                        batchCount, orders.size(), toMigrate.size(), totalMigrated);
            } else {
                log.info("第{}批：查询{}条，无需迁移", batchCount, orders.size());
            }
            
            // ═══════════════════════════════════════════════════════
            // 第4步：更新游标，准备下一批
            // ═══════════════════════════════════════════════════════
            lastId = orders.get(orders.size() - 1).getId();
            
            // ═══════════════════════════════════════════════════════
            // 第5步：控制迁移速度，避免数据库压力过大
            // ═══════════════════════════════════════════════════════
            // 每10批休眠100ms
            sleep(batchCount);  
        }
        
        log.info("✅ 数据迁移完成：共迁移 {} 条数据", totalMigrated);
        log.info("需要更新路由映射的订单号虚拟分片ID数量: {}", orderLogicalShardIds.size());
        
        return new MigrationResult(totalMigrated, orderLogicalShardIds);
    }
    
    public void sleep(int batchCount){
        // 步骤5：防止过快，可选的休眠
        final int ten = 10;
        if (batchCount % ten == 0) {
            try {
                // 每10批休眠100ms，降低数据库压力
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 完整的迁移流程（包含数据迁移和路由表更新）
     * 
     * ⚠️ 核心修改：更新路由映射表时需要更新两类虚拟分片ID
     * 1. 用户ID的虚拟分片ID范围（startShardId ~ endShardId）
     * 2. 被迁移订单的订单号虚拟分片ID（可能不在上述范围内）
     *
     * @param startShardId 虚拟分片起始ID（基于用户ID）
     * @param endShardId 虚拟分片结束ID（基于用户ID）
     * @param sourceDatabase 源数据库
     * @param sourceTable 源表
     * @param targetDatabase 目标数据库
     * @param targetTable 目标表
     * @return 更新的路由映射记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int migrateVirtualShardRange(int startShardId,
                                        int endShardId,
                                        String sourceDatabase,
                                        String sourceTable,
                                        String targetDatabase,
                                        String targetTable) {
        log.info("开始迁移虚拟分片范围（基于用户ID）：{}-{}", startShardId, endShardId);
        log.info("源库表：{}.{}", sourceDatabase, sourceTable);
        log.info("目标库表：{}.{}", targetDatabase, targetTable);
        
        // 步骤1：执行数据迁移（基于用户ID判断）
        MigrationResult result = migrateTableData(sourceDatabase, sourceTable,
                targetDatabase, targetTable,
                startShardId, endShardId);
        
        log.info("✓ 数据迁移完成：迁移了 {} 条数据", result.migratedCount);
        
        // 步骤2：更新虚拟分片路由映射表
        int targetTableSuffix = extractTableSuffixInt(targetTable);
        String targetDbSuffix = getDatabaseSuffix(targetDatabase);
        
        // 2.1 更新用户ID的虚拟分片ID范围
        int userShardUpdatedCount = shardingRouteMappingMapper.update(
                null,
                Wrappers.lambdaUpdate(ShardingRouteMapping.class)
                        .set(ShardingRouteMapping::getPhysicalDatabaseSuffix, targetDbSuffix)
                        .set(ShardingRouteMapping::getPhysicalTableSuffix, targetTableSuffix)
                        .setSql("version = version + 1")
                        .ge(ShardingRouteMapping::getLogicalShardId, startShardId)
                        .le(ShardingRouteMapping::getLogicalShardId, endShardId)
        );
        
        log.info("✓ 用户ID虚拟分片路由映射更新完成：更新了 {} 条记录", userShardUpdatedCount);
        
        // 2.2 更新被迁移订单的订单号虚拟分片ID
        int orderShardUpdatedCount = 0;
        if (!result.orderLogicalShardIds.isEmpty()) {
            log.info("开始更新 {} 个订单号的虚拟分片路由映射...", result.orderLogicalShardIds.size());
            
            for (Integer orderLogicalShardId : result.orderLogicalShardIds) {
                int count = shardingRouteMappingMapper.update(
                        null,
                        Wrappers.lambdaUpdate(ShardingRouteMapping.class)
                                .set(ShardingRouteMapping::getPhysicalDatabaseSuffix, targetDbSuffix)
                                .set(ShardingRouteMapping::getPhysicalTableSuffix, targetTableSuffix)
                                .setSql("version = version + 1")
                                .eq(ShardingRouteMapping::getLogicalShardId, orderLogicalShardId)
                );
                orderShardUpdatedCount += count;
            }
            
            log.info("✓ 订单号虚拟分片路由映射更新完成：更新了 {} 条记录", orderShardUpdatedCount);
        }
        
        int totalUpdatedCount = userShardUpdatedCount + orderShardUpdatedCount;
        log.info("✓ 路由映射表总计更新：{} 条记录（用户ID: {}, 订单号: {}）",
                totalUpdatedCount, userShardUpdatedCount, orderShardUpdatedCount);
        
        // 步骤3：刷新缓存
        orderClient.reloadRouteMappingCache();
        log.info("✓ 路由缓存刷新完成");
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("✅ 虚拟分片范围 {}-{} 迁移完成！", startShardId, endShardId);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        return totalUpdatedCount;
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
        int updatedCount = shardingRouteMappingMapper.update(
                null,
                Wrappers.lambdaUpdate(ShardingRouteMapping.class)
                        .set(ShardingRouteMapping::getPhysicalDatabaseSuffix, getDatabaseSuffix(originalDatabase))
                        .set(ShardingRouteMapping::getPhysicalTableSuffix, originalTableSuffix)
                        .setSql("version = version + 1")
                        .ge(ShardingRouteMapping::getLogicalShardId, startShardId)
                        .le(ShardingRouteMapping::getLogicalShardId, endShardId)
        );
        
        // 刷新缓存
        orderClient.reloadRouteMappingCache();
        
        log.info("✅ 回滚完成，虚拟分片 {}-{} 已恢复到 {}.{}",
                startShardId, endShardId, originalDatabase, originalTable);
        
        return updatedCount;
    }
    
    /**
     * 清理源表中已迁移的数据
     * 
     * ⚠️ 核心修改：基于用户ID的虚拟分片ID判断是否删除（与迁移逻辑保持一致）
     *
     * @param sourceDatabase 源数据库名称
     * @param sourceTable    源表名称
     * @param startShardId   虚拟分片起始ID（基于用户ID）
     * @param endShardId     虚拟分片结束ID（基于用户ID）
     * @return 删除的数据条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupSourceTableData(String sourceDatabase, String sourceTable,
                                      int startShardId, int endShardId) {
        
        log.info("开始清理源表数据：{}.{}", sourceDatabase, sourceTable);
        log.info("虚拟分片范围（基于用户ID）：{}-{}", startShardId, endShardId);
        
        int pageSize = 1000;
        long lastId = 0;
        int totalDeleted = 0;
        int batchCount = 0;
        
        while (true) {
            batchCount++;
            
            // ═══════════════════════════════════════════════════════
            // 第1步：查询源表数据
            // ═══════════════════════════════════════════════════════
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
            
            // ═══════════════════════════════════════════════════════
            // 第2步：过滤需要删除的数据
            // ⚠️ 基于用户ID判断（与迁移逻辑保持一致）
            // ═══════════════════════════════════════════════════════
            List<Long> toDeleteIds = new ArrayList<>();
            for (Order order : orders) {
                // ✅ 基于用户ID判断是否删除
                int userLogicalShardId = VirtualShardingAlgorithmFunc
                        .calculateLogicalShardId(order.getUserId());
                
                // 只删除已迁移的数据（用户ID在指定虚拟分片范围内）
                if (userLogicalShardId >= startShardId && userLogicalShardId <= endShardId) {
                    toDeleteIds.add(order.getId());
                }
            }
            
            // ═══════════════════════════════════════════════════════
            // 第3步：批量删除
            // ═══════════════════════════════════════════════════════
            if (!toDeleteIds.isEmpty()) {
                try (HintManager hintManager = HintManager.getInstance()) {
                    String dbSuffix = extractDatabaseSuffix(sourceDatabase);
                    String tableSuffix = extractTableSuffix(sourceTable);
                    
                    hintManager.addDatabaseShardingValue("d_order", dbSuffix);
                    hintManager.addTableShardingValue("d_order", tableSuffix);
                    
                    // 物理删除
                    int deleteCount = orderMapper.physicalDeleteByIds(toDeleteIds);
                    totalDeleted += deleteCount;
                    
                    log.info("第{}批：查询{}条，删除{}条，累计删除{}条",
                            batchCount, orders.size(), deleteCount, totalDeleted);
                }
            } else {
                log.info("第{}批：查询{}条，无需删除", batchCount, orders.size());
            }
            
            // 更新游标
            lastId = orders.get(orders.size() - 1).getId();
            
            // 控制删除速度
            sleep(batchCount);
        }
        
        log.info("✅ 源表数据清理完成：共删除 {} 条数据", totalDeleted);
        
        return totalDeleted;
    }
    
    /**
     * 从表名中提取表后缀
     * @param tableName 完整表名（如 d_order_4）
     * @return 表后缀字符串如 "4" - 注意返回字符串，因为 HintManager 需要
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
    
    /**
     * 迁移结果
     */
    public static class MigrationResult {
        /**
         * 迁移的数据条数
         * */
        public final int migratedCount;
        /**
         * 需要更新路由映射的订单号虚拟分片ID集合
         * */
        public final Set<Integer> orderLogicalShardIds;
        
        public MigrationResult(int migratedCount, Set<Integer> orderLogicalShardIds) {
            this.migratedCount = migratedCount;
            this.orderLogicalShardIds = orderLogicalShardIds;
        }
    }
}