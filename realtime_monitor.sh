#!/bin/bash

echo "=== 大麦网压测实时监控脚本 ==="
echo "开始时间: $(date)"
echo "=========================================="

# 监控间隔（秒）
INTERVAL=3

# 监控次数
COUNT=20

for i in $(seq 1 $COUNT); do
    echo ""
    echo "=== 监控轮次 $i/20 - $(date) ==="
    
    # 1. 系统负载
    echo "【系统负载】"
    uptime | awk '{print "负载: " $10 " " $11 " " $12}'
    
    # 2. CPU使用率
    echo "【CPU使用率】"
    top -l 1 | grep "CPU usage" | awk '{print "CPU: User=" $3 " Sys=" $5 " Idle=" $7}'
    
    # 3. 内存使用
    echo "【内存使用】"
    vm_stat | grep -E "(free|active|inactive|wired)" | head -4 | while read line; do
        echo "  $line"
    done
    
    # 4. 线程数统计
    echo "【线程数统计】"
    total_threads=$(ps -M | wc -l)
    echo "  系统总线程数: $total_threads"
    
    # 5. 关键Java进程资源使用
    echo "【关键进程资源】"
    ps aux | grep java | grep -v grep | head -5 | while read line; do
        pid=$(echo $line | awk '{print $2}')
        cpu=$(echo $line | awk '{print $3}')
        mem=$(echo $line | awk '{print $4}')
        cmd=$(echo $line | awk '{print $11}' | sed 's/.*\///')
        echo "  PID:$pid CPU:${cpu}% MEM:${mem}% CMD:$cmd"
    done
    
    # 6. 网络连接数
    echo "【网络连接】"
    established=$(netstat -an | grep ESTABLISHED | wc -l)
    echo "  ESTABLISHED连接数: $established"
    
    # 7. IO统计
    echo "【IO统计】"
    iostat 1 1 | tail -1 | awk '{print "  磁盘IO: " $3 "tps " $4 "MB/s"}'
    
    echo "----------------------------------------"
    
    # 如果不是最后一次，等待间隔时间
    if [ $i -lt $COUNT ]; then
        sleep $INTERVAL
    fi
done

echo ""
echo "=== 监控完成 - $(date) ==="
echo "=========================================="
