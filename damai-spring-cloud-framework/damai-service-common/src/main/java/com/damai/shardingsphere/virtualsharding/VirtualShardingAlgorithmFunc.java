package com.damai.shardingsphere.virtualsharding;

public class VirtualShardingAlgorithmFunc {
    
    /** 原始分库数量 */
    public static final int ORIGINAL_DATABASE_COUNT = 2;
    
    /** 原始分表数量 */
    public static final int ORIGINAL_TABLE_COUNT = 4;
    
    /** 虚拟分片总数（固定） */
    private static final int VIRTUAL_SHARD_COUNT = 1024;
    
    /**
     * 计算虚拟分片ID
     */
    public static int calculateLogicalShardId(Long shardingKey) {
        // 步骤1：提取基因位，计算物理分片索引
        int physicalShardIndex = calculatePhysicalShardIndex(shardingKey);
        
        // 步骤2：计算虚拟分片偏移
        int totalPhysicalShards = ORIGINAL_DATABASE_COUNT * ORIGINAL_TABLE_COUNT;
        int virtualShardsPerPhysical = VIRTUAL_SHARD_COUNT / totalPhysicalShards;
        int virtualOffset = (int)(Math.abs(shardingKey) % virtualShardsPerPhysical);
        
        // 步骤3：计算虚拟分片ID
        return physicalShardIndex * virtualShardsPerPhysical + virtualOffset;
    }
    
    /**
     * 计算物理分片索引（基于基因位）
     */
    private static int calculatePhysicalShardIndex(Long shardingKey) {
        // 提取表索引
        int tableIndex = (int) ((ORIGINAL_TABLE_COUNT - 1) & shardingKey);
        
        // 提取库索引
        long tableGeneLength = log2N(ORIGINAL_TABLE_COUNT);
        int databaseIndex = (int) ((ORIGINAL_DATABASE_COUNT - 1) & (shardingKey >> tableGeneLength));
        
        // 组合成物理分片索引
        return databaseIndex * ORIGINAL_TABLE_COUNT + tableIndex;
    }
    
    private static long log2N(long count) {
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
         * @param tablePrefix 表前缀（如 d_order）
         * @return 完整表名（如 d_order_0）
         */
        public String getFullTableName(String tablePrefix) {
            return tablePrefix + "_" + tableSuffix;
        }
    }
}
