package com.damai.shardingsphere;

import com.damai.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ShardingExpansionService {
    
    @Autowired
    private VirtualShardMigrationService migrationService;
    
    @Autowired
    private OrderMapper orderMapper;
    
    /**
     * 执行完整的扩容流程
     * 场景：从2库×12表（3类表×4张）扩展到2库×24表（3类表×8张）
     */
    public void executeExpansion() {
        log.info("开始执行扩容流程：2库×12表 → 2库×24表");
        log.info("包含：订单表、购票人订单表、订单记录表");
        
        // 第1步：创建新表（手动执行SQL）
        // 详见文档 4.1.2 步骤1
        
        // 第2步：迁移damai_order_0库的所有表
        migrateDatabase("damai_order_0");
        
        // 第3步：迁移damai_order_1库的所有表
        migrateDatabase("damai_order_1");
        
        log.info("✅ 扩容流程执行完成");
    }
    
    /**
     * 迁移单个数据库的所有表（包含3类表）
     */
    private void migrateDatabase(String database) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始迁移 {} 库的所有表", database);
        
        // 1. 迁移订单表（d_order）
        log.info("【1/3】迁移订单表（d_order）");
        migrateSingleTable(database, "d_order_0", "d_order_4", 64, 127);
        migrateSingleTable(database, "d_order_1", "d_order_5", 192, 255);
        migrateSingleTable(database, "d_order_2", "d_order_6", 320, 383);
        migrateSingleTable(database, "d_order_3", "d_order_7", 448, 511);
        
        // 暂时不需要 2. 迁移购票人订单表（d_order_ticket_user）
        //log.info("【2/3】迁移购票人订单表（d_order_ticket_user）");
        //migrateSingleTable(database, "d_order_ticket_user_0", "d_order_ticket_user_4", 64, 127);
        //migrateSingleTable(database, "d_order_ticket_user_1", "d_order_ticket_user_5", 192, 255);
        //migrateSingleTable(database, "d_order_ticket_user_2", "d_order_ticket_user_6", 320, 383);
        //migrateSingleTable(database, "d_order_ticket_user_3", "d_order_ticket_user_7", 448, 511);
        
        // 暂时不需要 3. 迁移订单记录表（d_order_ticket_user_record）
        //log.info("【3/3】迁移订单记录表（d_order_ticket_user_record）");
        //migrateSingleTable(database, "d_order_ticket_user_record_0", "d_order_ticket_user_record_4", 64, 127);
        //migrateSingleTable(database, "d_order_ticket_user_record_1", "d_order_ticket_user_record_5", 192, 255);
        //migrateSingleTable(database, "d_order_ticket_user_record_2", "d_order_ticket_user_record_6", 320, 383);
        //migrateSingleTable(database, "d_order_ticket_user_record_3", "d_order_ticket_user_record_7", 448, 511);
        
        log.info("✅ {} 库迁移完成（4张表拆分为8张表）", database);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    /**
     * 迁移单张表
     */
    private void migrateSingleTable(String database, String sourceTable,
                                    String targetTable, int startShardId, int endShardId) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("开始迁移：{}.{} → {}.{}", database, sourceTable, database, targetTable);
        log.info("虚拟分片范围：{}-{}", startShardId, endShardId);
        
        try {
            // 调用完整的迁移流程（包含数据迁移、路由表更新、缓存刷新）
            int updatedCount = migrationService.migrateVirtualShardRange(
                    startShardId, endShardId,
                    // 源库表
                    database, sourceTable,
                    // 目标库表
                    database, targetTable
            );
            
            log.info("✅ {}.{} 迁移成功，更新了{}个虚拟分片映射", database, targetTable, updatedCount);
            
            // 可选：清理源表数据
            // ⚠️ 重要：建议先验证新表数据无误后再执行清理
            // 如果需要立即清理，请取消下面的注释
             int deletedCount = migrationService.cleanupSourceTableData(
                     database, sourceTable, startShardId, endShardId
             );
             log.info("✅ 源表数据清理完成，删除了{}条记录", deletedCount);
            
        } catch (Exception e) {
            log.error("❌ {}.{} 迁移失败：{}", database, sourceTable, e.getMessage(), e);
            
            // 出错时回滚路由表
            log.warn("开始回滚路由表...");
            migrationService.rollbackMigration(
                    startShardId, endShardId,
                    database, sourceTable
            );
            
            throw new RuntimeException("迁移失败，已回滚", e);
        }
        
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}