package com.damai.shardingsphere;

import com.damai.exception.DaMaiFrameException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 扩容服务
 * 
 * ⚠️ 核心修复说明：
 * 问题：订单号虽然融合了用户ID的基因位（后3位），但整体值不同，
 *      导致 orderNumber % 128 和 userId % 128 结果不同。
 *      如果按订单号的虚拟分片ID范围拆分，会导致同一用户的订单分散到不同表。
 * 
 * 解决：迁移策略改为基于用户ID的虚拟分片ID判断，保证同一用户的所有订单在同一张表。
 * 
 * 示例：
 * 用户ID:   logicalShardId = 258 (offset=2, 在前64个)
 * 订单号1:  logicalShardId = 266 (offset=10, 在前64个) ✅ 同表
 * 订单号2:  logicalShardId = 330 (offset=74, 在后64个) ✅ 如果按订单号判断会被拆分，但现在基于用户ID，不会拆分
 * 
 * 扩容后：
 * - 用户ID (258) → 保留在原表
 * - 订单号1 (266) → 保留在原表 ✅
 * - 订单号2 (330) → 保留在原表 ✅（路由映射已更新）
 */
@Slf4j
@Service
public class ShardingExpansionService {
    
    @Autowired
    private VirtualShardMigrationTask virtualShardMigrationTask;
    
    /**
     * 执行完整的扩容流程
     * 场景：从2库×4表 扩展 到2库×8表
     * 
     * 迁移策略：基于用户ID的虚拟分片ID范围拆分
     */
    public void executeExpansion() {
        log.info("开始执行扩容流程：2库×4表 → 2库×8表 包含：订单表");
        log.info("⚠️ 迁移策略：基于用户ID维度，保证同一用户的所有订单在同一张表");
        
        // 第1步：创建新表（手动执行SQL）
        
        // 第2步：迁移damai_order_0库的所有表
        migrateDatabase("damai_order_0");
        
        // 第3步：迁移damai_order_1库的所有表
        migrateDatabase("damai_order_1");
        
        log.info("✅ 扩容流程执行完成");
    }
    
    /**
     * 迁移单个数据库的所有表
     */
    private void migrateDatabase(String database) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始迁移 {} 库的所有表", database);
        
        // 1. 根据数据库名称确定虚拟分片的基础偏移量
        // damai_order_0 → 偏移量 0 (虚拟分片 0-511)
        // damai_order_1 → 偏移量 512 (虚拟分片 512-1023)
        int baseOffset = database.endsWith("_0") ? 0 : 512;
        log.info("数据库 {} 的虚拟分片基础偏移量：{}", database, baseOffset);
        
        // 2. 迁移订单表（d_order）的4张原表
        // d_order_0 → d_order_4 (虚拟分片 64-127)
        migrateSingleTable(database, "d_order_0", "d_order_4",
                baseOffset + 64, baseOffset + 127);
        
        // d_order_1 → d_order_5 (虚拟分片 192-255)
        migrateSingleTable(database, "d_order_1", "d_order_5",
                baseOffset + 192, baseOffset + 255);
        
        // d_order_2 → d_order_6 (虚拟分片 320-383)
        migrateSingleTable(database, "d_order_2", "d_order_6",
                baseOffset + 320, baseOffset + 383);
        
        // d_order_3 → d_order_7 (虚拟分片 448-511)
        migrateSingleTable(database, "d_order_3", "d_order_7",
                baseOffset + 448, baseOffset + 511);
        
        log.info("✅ {} 库迁移完成（4张表拆分为8张表）", database);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    /**
     * 迁移单张表
     */
    private void migrateSingleTable(String database, String sourceTable,
                                    String targetTable, int startShardId, int endShardId) {
        log.info("开始迁移：{}.{} → {}.{}", database, sourceTable, database, targetTable);
        log.info("虚拟分片范围：{}-{}", startShardId, endShardId);
        
        try {
            // 步骤1：调用完整迁移流程（数据迁移 + 路由更新 + 缓存刷新）
            // @param startShardId 起始虚拟分片ID（包含）
            // @param endShardId 结束虚拟分片ID（包含）
            // @param database 数据库名称
            // @param sourceTable 源表名称
            // @param targetTable 目标表名称
            // @return 更新的虚拟分片映射数量
            int updatedCount = virtualShardMigrationTask.migrateVirtualShardRange(
                    startShardId, endShardId,
                    database, sourceTable,
                    database, targetTable
            );
            
            log.info("✅ {}.{} 迁移成功，更新了{}个虚拟分片映射", database, targetTable, updatedCount);
            
            // 步骤2：清理源表数据
            int deletedCount = virtualShardMigrationTask.cleanupSourceTableData(
                    database, sourceTable, startShardId, endShardId
            );
            
            log.info("✅ 源表数据清理完成，删除了{}条记录", deletedCount);
            
        } catch (Exception e) {
            log.error("❌ {}.{} 迁移失败：{}", database, sourceTable, e.getMessage(), e);
            
            // 步骤3：异常时回滚路由表
            log.warn("开始回滚路由表...");
            virtualShardMigrationTask.rollbackMigration(startShardId, endShardId, database, sourceTable);
            
            throw new DaMaiFrameException("迁移失败，已回滚", e);
        }
    }
}