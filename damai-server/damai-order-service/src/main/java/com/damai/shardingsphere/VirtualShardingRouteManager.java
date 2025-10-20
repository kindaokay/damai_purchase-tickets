package com.damai.shardingsphere;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.damai.entity.ShardingRouteMapping;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.ShardingRouteMappingMapper;
import com.damai.shardingsphere.virtualsharding.VirtualShardingAlgorithmFunc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚拟分片路由管理器
 */
@Slf4j
@Component
public class VirtualShardingRouteManager implements InitializingBean {
    
    @Autowired
    private ShardingRouteMappingMapper shardingRouteMappingMapper;
    
    /** 路由缓存：虚拟分片ID → 物理分片 */
    private final Map<Integer, PhysicalShard> routeCache = new ConcurrentHashMap<>();
    
    /**
     * 初始化：加载路由映射
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        reloadRouteMappingCache();
        log.info("虚拟分片路由管理器初始化完成，共加载 {} 个路由映射", routeCache.size());
    }
    
    /**
     * 根据分片键路由到物理分片
     * 
     * @param shardingKey 分片键（orderNumber或userId）
     * @return 物理分片信息
     */
    public PhysicalShard route(Long shardingKey) {
        //计算虚拟分片ID
        int logicalShardId = VirtualShardingAlgorithmFunc.calculateLogicalShardId(shardingKey);
        PhysicalShard shard = routeCache.get(logicalShardId);
        if (Objects.isNull(shard)) {
            log.error("虚拟分片 {} 未配置路由映射！shardingKey={}", logicalShardId, shardingKey);
            throw new DaMaiFrameException("虚拟分片路由未配置: " + logicalShardId);
        }
        log.info("路由：shardingKey={} → 虚拟分片{} → {}.{}", 
                shardingKey, logicalShardId, shard.database(), shard.tableSuffix());
        return shard;
    }
    
    /**
     * 重新加载路由映射缓存（支持热更新）
     */
    public synchronized void reloadRouteMappingCache() {
        try {
            routeCache.clear();
            
            List<ShardingRouteMapping> mappings = shardingRouteMappingMapper.selectList(Wrappers.emptyWrapper());
            
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
            throw new DaMaiFrameException("路由映射加载失败", e);
        }
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