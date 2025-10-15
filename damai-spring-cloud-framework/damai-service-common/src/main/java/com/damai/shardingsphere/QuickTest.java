package com.damai.shardingsphere;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 快速测试代码
 * @author: 阿星不是程序员
 **/
public class QuickTest {
    public static void main(String[] args) {
        // 2库×4表=8个物理表
        int[] tableCounts = new int[8]; 
        
        // 模拟800个订单编号（或用户ID）
        for (long shardingKey = 1; shardingKey <= 800; shardingKey++) {
            // 分表
            int tableIndex = (int)((4 - 1) & shardingKey);
            // 分库
            int dbIndex = (int)((2 - 1) & (shardingKey >> 2));    
            int physicalTableIndex = dbIndex * 4 + tableIndex;
            tableCounts[physicalTableIndex]++;
        }
        
        System.out.println("各物理表数据分布：");
        for (int i = 0; i < 8; i++) {
            int db = i / 4;
            int tb = i % 4;
            System.out.println("DB" + db + "-T" + tb + ": " + tableCounts[i] + "条");
        }
        
        // 检查均匀性
        int min = Integer.MAX_VALUE, max = 0;
        for (int count : tableCounts) {
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
        
        System.out.println("\n期望：每表100条");
        System.out.println("实际：" + min + " ~ " + max + "条");
        System.out.println(max == min && max == 100 ? "✓ 测试通过" : "✗ 测试失败");
    }
}