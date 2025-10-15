package com.damai.shardingsphere;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.entity.ShardingRouteMapping;
import com.damai.mapper.ShardingRouteMappingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚拟分片路由管理器
 */
@Slf4j
@Component
public class VirtualShardingRouteManager implements InitializingBean {
    
    /** 虚拟分片总数（固定） */
    private static final int VIRTUAL_SHARD_COUNT = 1024;
    
    /** 原始分库数量 */
    @Value("${sharding.original.database-count:2}")
    public int originalDatabaseCount;
    
    /** 原始分表数量 */
    @Value("${sharding.original.table-count:4}")
    public int originalTableCount;
    
    @Autowired
    private ShardingRouteMappingMapper routeMappingMapper;
    
    /** 路由缓存：虚拟分片ID → 物理分片 */
    private final Map<Integer, PhysicalShard> routeCache = new ConcurrentHashMap<>();
    
    /**
     * 初始化：加载路由映射
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        reloadRouteMapping();
        log.info("虚拟分片路由管理器初始化完成，共加载 {} 个路由映射", routeCache.size());
    }
    
    /**
     * 根据分片键路由到物理分片
     * 
     * @param shardingKey 分片键（orderNumber或userId）
     * @return 物理分片信息
     */
    public PhysicalShard route(Long shardingKey) {
        int logicalShardId = calculateLogicalShardId(shardingKey);
        PhysicalShard shard = routeCache.get(logicalShardId);
        
        if (shard == null) {
            log.error("虚拟分片 {} 未配置路由映射！shardingKey={}", logicalShardId, shardingKey);
            throw new IllegalStateException("虚拟分片路由未配置: " + logicalShardId);
        }
        
        log.info("路由：shardingKey={} → 虚拟分片{} → {}.{}", 
                shardingKey, logicalShardId, shard.database(), shard.tableSuffix());
        
        return shard;
    }
    
    /**
     * 计算虚拟分片ID
     */
    public int calculateLogicalShardId(Long shardingKey) {
        // 步骤1：提取基因位，计算物理分片索引
        int physicalShardIndex = calculatePhysicalShardIndex(shardingKey);
        
        // 步骤2：计算虚拟分片偏移
        int totalPhysicalShards = originalDatabaseCount * originalTableCount;
        int virtualShardsPerPhysical = VIRTUAL_SHARD_COUNT / totalPhysicalShards;
        int virtualOffset = (int)(Math.abs(shardingKey) % virtualShardsPerPhysical);
        
        // 步骤3：计算虚拟分片ID
        int logicalShardId = physicalShardIndex * virtualShardsPerPhysical + virtualOffset;
        
        return logicalShardId;
    }
    
    /**
     * 计算物理分片索引（基于基因位）
     */
    private int calculatePhysicalShardIndex(Long shardingKey) {
        // 提取表索引
        int tableIndex = (int) ((originalTableCount - 1) & shardingKey);
        
        // 提取库索引
        long tableGeneLength = log2N(originalTableCount);
        int databaseIndex = (int) ((originalDatabaseCount - 1) & (shardingKey >> tableGeneLength));
        
        // 组合成物理分片索引
        return databaseIndex * originalTableCount + tableIndex;
    }
    
    /**
     * 重新加载路由映射（支持热更新）
     */
    public void reloadRouteMapping() {
        try {
            routeCache.clear();
            
            List<ShardingRouteMapping> mappings = routeMappingMapper.selectList(Wrappers.emptyWrapper());
            
            for (ShardingRouteMapping mapping : mappings) {
                PhysicalShard shard = new PhysicalShard(
                        mapping.getPhysicalDatabaseSuffix(),
                        mapping.getPhysicalTableSuffix()
                );
                routeCache.put(mapping.getLogicalShardId(), shard);
            }
            
            log.info("路由映射重新加载完成，共 {} 条", routeCache.size());
            
        } catch (Exception e) {
            log.error("路由映射加载失败", e);
            throw new RuntimeException("路由映射加载失败", e);
        }
    }
    
    private long log2N(long count) {
        return (long)(Math.log(count) / Math.log(2));
    }
    
    /**
     * 物理分片信息
     */
    public record PhysicalShard(String database, int tableSuffix) {
        /**
         * 获取数据源名称
         */
        public String getDatasourceName() {
            return database;
        }
        
        /**
         * 根据表前缀生成完整表名
         * @param tablePrefix 表前缀（如 d_order、d_order_ticket_user）
         * @return 完整表名（如 d_order_0、d_order_ticket_user_1）
         */
        public String getFullTableName(String tablePrefix) {
            return tablePrefix + "_" + tableSuffix;
        }
    }
}