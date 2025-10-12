package com.damai.shardingsphere;

import java.util.HashMap;
import java.util.Map;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 分库分表算法测试程序。功能：验证改进后的分库分表算法在各种配置下的数据分布情况
 * @author: 阿星不是程序员
 **/
public class ShardingAlgorithmTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   分库分表算法测试 - 详细分析版本");
        System.out.println("========================================\n");
        
        // 测试非对称配置（实际生产常用）
        System.out.println("【第一部分】非对称配置测试（实际场景）\n");
        // 重点：2个库×4张表
        testWithDetailedOutput(2, 4, 32);
        testConfiguration(2, 8, 64);
        // 重点：4个库×8张表
        testConfiguration(4, 8, 128);
        // 重点：4个库×16张表
        testConfiguration(4, 16, 256);
        testConfiguration(8, 16, 512);
        
        // 测试对称配置（分库数 = 分表数）
        System.out.println("\n【第二部分】对称配置测试\n");
        testConfiguration(2, 2, 20);
        testConfiguration(4, 4, 64);
        testConfiguration(8, 8, 256);
        testConfiguration(16, 16, 1024);
        testConfiguration(32, 32, 4096);
    }
    
    /**
     * 详细输出版本 - 展示每个ID的分配过程
     */
    public static void testWithDetailedOutput(int dbCount, int tableCount, int testCount) {
        System.out.println("┌─────────────────────────────────────────────────────┐");
        System.out.println("│ 配置：" + dbCount + "个库 × " + tableCount + "个表 = " 
                         + (dbCount * tableCount) + "个物理表");
        System.out.println("│ 测试数据量：" + testCount + "条");
        System.out.println("└─────────────────────────────────────────────────────┘\n");
        
        // 计算基因长度
        int tableGeneBits = (int)log2N(tableCount);
        int dbGeneBits = (int)log2N(dbCount);
        
        System.out.println("算法参数：");
        System.out.println("  - 表基因占用：" + tableGeneBits + " 位（取ID二进制的最后" + tableGeneBits + "位）");
        System.out.println("  - 库基因占用：" + dbGeneBits + " 位（取ID右移" + tableGeneBits + "位后的最后" + dbGeneBits + "位）");
        System.out.println();
        
        // 显示前10个ID的详细分配过程
        System.out.println("前10个ID的详细分配过程：");
        System.out.println("┌────┬──────────┬────────┬────────┬──────────┬────────┬──────────┐");
        System.out.println("│ ID │ 二进制   │ 表基因 │ 分表   │ 右移后   │ 库基因 │ 最终位置 │");
        System.out.println("├────┼──────────┼────────┼────────┼──────────┼────────┼──────────┤");
        
        Map<String, Integer> distribution = new HashMap<>();
        
        for (long id = 1; id <= testCount && id <= 10; id++) {
            // 计算分表
            long tableIndex = (tableCount - 1) & id;
            
            // 计算分库
            long shiftedId = id >> tableGeneBits;
            long dbIndex = (dbCount - 1) & shiftedId;
            
            // 统计
            String location = "DB" + dbIndex + "-T" + tableIndex;
            distribution.put(location, distribution.getOrDefault(location, 0) + 1);
            
            // 格式化输出
            String binary = String.format("%8s", Long.toBinaryString(id)).replace(' ', '0');
            String tableGene = binary.substring(binary.length() - tableGeneBits);
            String shiftedBinary = String.format("%8s", Long.toBinaryString(shiftedId)).replace(' ', '0');
            String dbGene = shiftedBinary.substring(shiftedBinary.length() - dbGeneBits);
            
            System.out.printf("│%3d │ %8s │   %s   │  表%-2d  │ %8s │   %s   │  %s  │\n",
                id, binary, tableGene, tableIndex, shiftedBinary, dbGene, location);
        }
        
        // 继续处理剩余的ID（不打印详情）
        for (long id = 11; id <= testCount; id++) {
            long tableIndex = (tableCount - 1) & id;
            long dbIndex = (dbCount - 1) & (id >> tableGeneBits);
            String location = "DB" + dbIndex + "-T" + tableIndex;
            distribution.put(location, distribution.getOrDefault(location, 0) + 1);
        }
        
        System.out.println("└────┴──────────┴────────┴────────┴──────────┴────────┴──────────┘\n");
        
        // 统计结果
        printStatistics(dbCount, tableCount, testCount, distribution);
    }
    
    /**
     * 标准测试（不显示详细过程）
     */
    public static void testConfiguration(int dbCount, int tableCount, int testCount) {
        System.out.println("┌─────────────────────────────────────────────────────┐");
        System.out.println("│ 配置：" + dbCount + "个库 × " + tableCount + "个表 = " 
                         + (dbCount * tableCount) + "个物理表");
        System.out.println("└─────────────────────────────────────────────────────┘");
        
        Map<String, Integer> distribution = new HashMap<>();
        
        for (long id = 1; id <= testCount; id++) {
            long tableIndex = (tableCount - 1) & id;
            long dbIndex = (dbCount - 1) & (id >> log2N(tableCount));
            String location = "DB" + dbIndex + "-T" + tableIndex;
            distribution.put(location, distribution.getOrDefault(location, 0) + 1);
        }
        
        printStatistics(dbCount, tableCount, testCount, distribution);
    }
    
    /**
     * 打印统计信息
     */
    private static void printStatistics(int dbCount, int tableCount, int testCount, 
                                       Map<String, Integer> distribution) {
        int totalShards = dbCount * tableCount;
        int usedShards = distribution.size();
        int expectedPerShard = testCount / totalShards;
        
        System.out.println("测试数据量：" + testCount + "条");
        System.out.println("使用的分片：" + usedShards + " / " + totalShards);
        
        if (usedShards == totalShards) {
            System.out.println("✓ 所有分片都被使用");
        } else {
            System.out.println("✗ 警告：有 " + (totalShards - usedShards) + " 个分片未被使用");
        }
        
        // 计算分布情况
        int maxCount = 0;
        int minCount = Integer.MAX_VALUE;
        for (int count : distribution.values()) {
            maxCount = Math.max(maxCount, count);
            minCount = Math.min(minCount, count);
        }
        
        System.out.println("期望每片：" + expectedPerShard + "条");
        System.out.println("实际范围：" + minCount + " ~ " + maxCount + "条");
        
        if (maxCount - minCount <= 1) {
            System.out.println("✓ 数据分布均匀");
        } else {
            double variance = ((double)(maxCount - minCount) / expectedPerShard * 100);
            System.out.println("偏差：" + String.format("%.1f%%", variance));
        }
        
        // 如果配置较小，显示详细分布
        if (dbCount <= 4 && tableCount <= 4) {
            System.out.println("\n详细分布表：");
            for (int db = 0; db < dbCount; db++) {
                System.out.print("  库" + db + "：");
                for (int tb = 0; tb < tableCount; tb++) {
                    String key = "DB" + db + "-T" + tb;
                    int count = distribution.getOrDefault(key, 0);
                    System.out.print(" [表" + tb + ":" + count + "条] ");
                }
                System.out.println();
            }
        }
        
        System.out.println("\n" + "=".repeat(57) + "\n");
    }
    
    /**
     * 计算log2(n)
     */
    public static long log2N(long n) {
        return (long)(Math.log(n) / Math.log(2));
    }
}