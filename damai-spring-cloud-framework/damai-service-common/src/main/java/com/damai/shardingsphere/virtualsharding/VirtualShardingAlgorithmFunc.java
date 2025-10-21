package com.damai.shardingsphere.virtualsharding;

import static com.damai.constant.ProgramOrderConstant.ORIGINAL_DATABASE_COUNT;
import static com.damai.constant.ProgramOrderConstant.ORIGINAL_TABLE_COUNT;
import static com.damai.constant.ProgramOrderConstant.VIRTUAL_SHARD_COUNT;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 计算虚拟分片ID
 * @author: 阿星不是程序员
 **/
public class VirtualShardingAlgorithmFunc {
    
    /**
     * 计算虚拟分片ID
     *
     * 算法：
     * 1. 提取基因位（3位）→ 确定物理分片索引（0-7）→ 确定"大区域"
     * 2. 使用分片键模128 → 确定虚拟分片内偏移（0-127）→ 确定"小区域"
     * 3. 计算虚拟分片ID = 物理分片索引 × 128 + 虚拟偏移 → 最终位置
     *
     * @param shardingKey 分片键（orderNumber或userId）
     * @return 虚拟分片ID（0-1023）
     */
    public static int calculateLogicalShardId(Long shardingKey) {
        // 步骤1：提取基因位，计算物理分片索引（确定"大区域"）
        int physicalShardIndex = calculatePhysicalShardIndex(shardingKey);
        // 例如：shardingKey=1234567890，基因=010 → physicalShardIndex=2
        // 含义：数据属于物理分片2（damai_order_0.d_order_2）
        
        // 步骤2：计算每个物理分片的虚拟分片数
        // 8
        int totalPhysicalShards = ORIGINAL_DATABASE_COUNT * ORIGINAL_TABLE_COUNT;
        // 1024/8=128
        int virtualShardsPerPhysical = VIRTUAL_SHARD_COUNT / totalPhysicalShards;
        // 含义：每个物理分片细分为128个虚拟分片
        
        // 步骤3：使用分片键模128，确定在128个虚拟分片中的位置（确定"小区域"）
        int virtualOffset = (int)(Math.abs(shardingKey) % virtualShardsPerPhysical);
        // 例如：shardingKey=1234567890 % 128 = 82
        // 含义：在物理分片2的128个虚拟分片中，位于第82个
        
        // 步骤4：计算最终的虚拟分片ID（组合"大区域"和"小区域"）
        int logicalShardId = physicalShardIndex * virtualShardsPerPhysical + virtualOffset;
        // 例如：2 × 128 + 82 = 256 + 82 = 338
        // 含义：全局虚拟分片ID = 338
        //      属于物理分片2的虚拟分片范围（256-383）中的第338号
        
        return logicalShardId;
    }
    
    /**
     * 计算物理分片索引（基于基因位）
     *
     * 算法：
     * 1. 提取表索引（后2位基因）
     * 2. 提取库索引（第3位基因，跳过表基因）
     * 3. 组合成物理分片索引（二维转一维）
     */
    private static int calculatePhysicalShardIndex(Long shardingKey) {
        // 提取表索引（低位基因）
        int tableIndex = (int) ((ORIGINAL_TABLE_COUNT - 1) & shardingKey);
        // 例如：(4-1) & 1234567890 = 3 & ...10 = 2 → 表2
        
        // 提取库索引（中位基因，跳过表基因位）
        long tableGeneLength = log2N(ORIGINAL_TABLE_COUNT);
        int databaseIndex = (int) ((ORIGINAL_DATABASE_COUNT - 1) & (shardingKey >> tableGeneLength));
        // 例如：(2-1) & (1234567890 >> 2) = 1 & ...00 = 0 → 库0
        
        // 组合成物理分片索引（二维坐标转一维索引）
        // 公式：行号(库) × 每行元素数(表数) + 列号(表)
        return databaseIndex * ORIGINAL_TABLE_COUNT + tableIndex;
        // 例如：0 × 4 + 2 = 2 → 物理分片索引2（对应damai_order_0.d_order_2）
    }
    
    private static long log2N(long count) {
        return (long)(Math.log(count) / Math.log(2));
    }
    
}
