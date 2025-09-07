# 高并发压测JVM优化建议

## 🚀 针对每秒500请求压测的JVM启动参数优化

### 1. 内存配置
```bash
# 堆内存配置 (根据服务器内存调整)
-Xms4g -Xmx4g                    # 初始和最大堆内存4GB
-Xmn1g                           # 新生代内存1GB
-XX:MetaspaceSize=256m           # 元空间初始大小
-XX:MaxMetaspaceSize=512m        # 元空间最大大小
```

### 2. GC优化配置
```bash
# 使用G1GC (推荐用于高并发场景)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100         # 最大GC暂停时间100ms
-XX:G1HeapRegionSize=16m         # G1堆区域大小
-XX:+G1UseAdaptiveIHOP          # 自适应IHOP
-XX:G1MixedGCCountTarget=8      # 混合GC目标次数

# 或者使用ParallelGC (另一个选择)
# -XX:+UseParallelGC
# -XX:ParallelGCThreads=8
```

### 3. 线程和并发优化
```bash
-XX:+UseBiasedLocking           # 启用偏向锁
-XX:+OptimizeStringConcat       # 优化字符串连接
-XX:+UseCompressedOops          # 压缩对象指针
-XX:+UseCompressedClassPointers # 压缩类指针
```

### 4. 网络和IO优化
```bash
-Djava.net.preferIPv4Stack=true        # 优先使用IPv4
-Djava.awt.headless=true               # 无头模式
-Dfile.encoding=UTF-8                  # 文件编码
```

### 5. 完整的启动命令示例
```bash
java -server \
  -Xms4g -Xmx4g -Xmn1g \
  -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=100 \
  -XX:G1HeapRegionSize=16m \
  -XX:+G1UseAdaptiveIHOP \
  -XX:G1MixedGCCountTarget=8 \
  -XX:+UseBiasedLocking \
  -XX:+OptimizeStringConcat \
  -XX:+UseCompressedOops \
  -XX:+UseCompressedClassPointers \
  -Djava.net.preferIPv4Stack=true \
  -Djava.awt.headless=true \
  -Dfile.encoding=UTF-8 \
  -jar your-application.jar
```

### 6. 监控参数 (用于调试)
```bash
# GC日志
-XX:+PrintGC
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-XX:+PrintGCDateStamps
-Xloggc:gc.log

# JVM调试
-XX:+PrintCommandLineFlags
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=./heapdump.hprof
```

## 📊 性能监控建议

1. **使用JProfiler或VisualVM**监控线程使用情况
2. **监控GC频率和暂停时间**
3. **观察内存使用模式**
4. **检查线程池队列积压情况**

## ⚠️ 注意事项

1. 根据实际服务器内存调整堆内存大小
2. 压测时监控CPU和内存使用率
3. 逐步调整参数，避免一次性改动过大
4. 在测试环境充分验证后再应用到生产环境
