# Java OOM问题排查指南

## 目录
1. [OOM问题概述](#1-oom问题概述)
2. [jmap工具详解](#2-jmap工具详解)
3. [OOM排查完整流程](#3-oom排查完整流程)
4. [内存分析工具使用](#4-内存分析工具使用)
5. [常见OOM场景](#5-常见oom场景)
6. [实际案例分析](#6-实际案例分析)
7. [预防措施](#7-预防措施)

---

## 1. OOM问题概述

### 1.1 什么是OOM

**OutOfMemoryError（OOM）** 是Java应用程序中最常见的内存问题，当JVM无法分配足够的内存时抛出。

### 1.2 常见OOM类型

| OOM类型 | 描述 | 常见原因 |
|---------|------|----------|
| `java.lang.OutOfMemoryError: Java heap space` | 堆内存不足 | 对象创建过多、内存泄漏 |
| `java.lang.OutOfMemoryError: GC overhead limit exceeded` | GC开销过大 | 频繁GC但回收效果差 |
| `java.lang.OutOfMemoryError: Direct buffer memory` | 直接内存不足 | NIO操作、Netty使用不当 |
| `java.lang.OutOfMemoryError: Metaspace` | 元空间不足 | 类加载过多、动态代理 |
| `java.lang.OutOfMemoryError: Compressed class space` | 压缩类空间不足 | 类定义过多 |

---

## 2. jmap工具详解

### 2.1 jmap基本介绍

**jmap（Java Memory Map）** 是JDK自带的内存分析工具，用于生成堆内存快照和查看内存使用情况。

### 2.2 jmap常用命令

#### 2.2.1 生成堆内存快照（Heap Dump）

```bash
# 基本语法
jmap -dump:format=b,file=<文件名> <进程ID>

# 实际示例
jmap -dump:format=b,file=heapdump.hprof 12345

# 只dump存活对象（推荐）
jmap -dump:live,format=b,file=heapdump_live.hprof 12345

# 自动触发dump（当OOM发生时）
# JVM启动参数添加：
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/path/to/dumps/
```

#### 2.2.2 查看堆内存使用情况

```bash
# 查看堆内存统计
jmap -heap 12345

# 查看类实例统计（按实例数量排序）
jmap -histo 12345

# 查看存活对象统计
jmap -histo:live 12345

# 输出到文件
jmap -histo 12345 > heap_histo.txt
```

#### 2.2.3 查看类加载器统计

```bash
# 查看类加载器信息
jmap -clstats 12345
```

### 2.3 jmap输出解读

#### 2.3.1 heap输出解读

```
Attaching to process ID 12345, please wait...
Debugging symbols for the JVM are not available.
using thread-local object allocation.
Parallel GC with 4 thread(s)

Heap Configuration:
   MinHeapFreeRatio         = 0
   MaxHeapFreeRatio         = 100
   MaxHeapSize              = 2147483648 (2048.0MB)
   NewSize                  = 715653120 (682.5MB)
   MaxNewSize               = 715653120 (682.5MB)
   OldSize                  = 1431830528 (1365.5MB)
   NewRatio                 = 2
   SurvivorRatio            = 8
   MetaspaceSize            = 21807104 (20.796875MB)
   CompressedClassSpaceSize = 1073741824 (1024.0MB)
   MaxMetaspaceSize         = 17592186044415 MB
   G1HeapRegionSize         = 0 (0.0MB)

Heap Usage:
PS Young Generation
Eden Space:
   capacity = 536870912 (512.0MB)
   used     = 423456789 (403.8MB)
   free     = 113414123 (108.2MB)
   78.87% used
From Space:
   capacity = 89478144 (85.25MB)
   used     = 0 (0.0MB)
   free     = 89478144 (85.25MB)
   0.0% used
To Space:
   capacity = 89478144 (85.25MB)
   used     = 0 (0.0MB)
   free     = 89478144 (85.25MB)
   0.0% used
PS Old Generation
   capacity = 1431830528 (1365.5MB)
   used     = 1200000000 (1144.4MB)
   free     = 231830528 (221.1MB)
   83.8% used
```

**关键指标解读：**
- **Eden Space使用率**：新生代使用情况，过高可能导致频繁Minor GC
- **Old Generation使用率**：老年代使用情况，过高可能导致Full GC
- **Metaspace使用情况**：类元数据空间，过高可能导致Metaspace OOM

#### 2.3.2 histo输出解读

```
 num     #instances         #bytes  class name
----------------------------------------------
   1:        123456       12345678  [C
   2:         98765        9876543  java.lang.String
   3:         45678        4567890  [Ljava.lang.Object;
   4:         23456        2345678  java.util.HashMap$Node
   5:         12345        1234567  [B
```

**字段说明：**
- **num**：排名
- **#instances**：实例数量
- **#bytes**：占用字节数
- **class name**：类名（[C表示char数组，[B表示byte数组）

---

## 3. OOM排查完整流程

### 3.1 问题发现阶段

#### 3.1.1 监控告警

```bash
# 1. 查看系统资源使用情况
top -p <进程ID>
free -h

# 2. 查看JVM进程状态
jps -v

# 3. 查看GC情况
jstat -gc <进程ID> 1s
```

#### 3.1.2 日志分析

```bash
# 查看应用日志中的OOM信息
grep -i "outofmemoryerror" /path/to/application.log

# 查看GC日志（如果开启了GC日志）
# JVM参数：-Xloggc:gc.log -XX:+PrintGCDetails
tail -f gc.log
```

### 3.2 信息收集阶段

#### 3.2.1 收集基本信息

```bash
# 1. 获取进程ID
jps | grep YourApplication

# 2. 查看JVM启动参数
jcmd <进程ID> VM.command_line

# 3. 查看JVM版本信息
java -version

# 4. 查看系统信息
uname -a
cat /proc/meminfo
```

#### 3.2.2 生成内存快照

```bash
# 生成heap dump（推荐在问题发生时立即执行）
jmap -dump:live,format=b,file=oom_$(date +%Y%m%d_%H%M%S).hprof <进程ID>

# 如果进程已经挂掉，查看是否有自动生成的dump文件
# 检查JVM参数中的HeapDumpPath路径
ls -la /path/to/dumps/
```

#### 3.2.3 收集运行时信息

```bash
# 查看线程信息
jstack <进程ID> > thread_dump_$(date +%Y%m%d_%H%M%S).txt

# 查看内存使用统计
jmap -histo <进程ID> > histo_$(date +%Y%m%d_%H%M%S).txt

# 查看GC统计
jstat -gc <进程ID> > gc_stats_$(date +%Y%m%d_%H%M%S).txt
```

### 3.3 问题分析阶段

#### 3.3.1 快速分析

```bash
# 1. 查看heap dump文件大小
ls -lh *.hprof

# 2. 使用jhat进行快速分析（简单场景）
jhat -J-Xmx4g oom_20240101_120000.hprof
# 然后访问 http://localhost:7000

# 3. 分析histo输出，查看占用内存最多的类
head -20 histo_20240101_120000.txt
```

#### 3.3.2 深度分析（使用MAT）

详见第4节内容。

---

## 4. 内存分析工具使用

### 4.1 Eclipse MAT（Memory Analyzer Tool）

#### 4.1.1 MAT安装

```bash
# 下载MAT
wget https://www.eclipse.org/downloads/download.php?file=/mat/1.14.0/rcp/MemoryAnalyzer-1.14.0.20230315-linux.gtk.x86_64.zip

# 解压
unzip MemoryAnalyzer-1.14.0.20230315-linux.gtk.x86_64.zip

# 启动MAT
./MemoryAnalyzer
```

#### 4.1.2 MAT基本使用

**1. 打开Heap Dump**
- File → Open Heap Dump
- 选择.hprof文件
- 等待解析完成

**2. 查看Overview报告**
```
- Leak Suspects：内存泄漏疑点
- Top Consumers：内存消耗大户
- Duplicate Classes：重复类
```

**3. 使用Dominator Tree**
- 显示对象的支配树
- 找出占用内存最多的对象
- 分析对象引用关系

**4. 使用Histogram**
- 按类分组显示对象统计
- 查看每个类的实例数和内存占用
- 对比不同时间点的快照

#### 4.1.3 MAT高级功能

**1. OQL查询**
```sql
-- 查找所有String对象
SELECT * FROM java.lang.String

-- 查找大于1MB的对象
SELECT * FROM INSTANCEOF java.lang.Object WHERE @retainedHeapSize > 1048576

-- 查找特定包下的对象
SELECT * FROM "com.example.ai_agent.*"

-- 查找可能的内存泄漏
SELECT * FROM java.util.HashMap WHERE @retainedHeapSize > 1000000
```

**2. 路径分析**
- Path to GC Roots：查看对象到GC根的引用路径
- Merge Shortest Paths to GC Roots：合并最短路径
- 分析为什么对象无法被回收

**3. 对比分析**
- Compare Baskets：对比不同对象集合
- 分析内存增长趋势
- 找出新增或减少的对象

### 4.2 其他分析工具

#### 4.2.1 jhat（JVM自带）

```bash
# 启动jhat分析
jhat -J-Xmx4g -port 7000 heapdump.hprof

# 访问Web界面
http://localhost:7000

# 常用查询
# 查看所有类：http://localhost:7000/histo/
# 查看实例：http://localhost:7000/instances/<class>
# 执行OQL：http://localhost:7000/oql/
```

#### 4.2.2 VisualVM

```bash
# 启动VisualVM
jvisualvm

# 功能特点
- 实时监控JVM状态
- 生成和分析heap dump
- 性能分析和采样
- MBean管理
```

#### 4.2.3 JProfiler（商业工具）

```bash
# 特点
- 强大的内存分析功能
- 实时监控和分析
- 友好的用户界面
- 支持远程分析
```

---

## 5. 常见OOM场景

### 5.1 堆内存OOM

#### 5.1.1 场景1：大对象创建

**问题代码：**
```java
public class BigObjectOOM {
    public static void main(String[] args) {
        // 创建大数组导致OOM
        int[] bigArray = new int[Integer.MAX_VALUE - 2];
    }
}
```

**排查方法：**
```bash
# 1. 查看histo，找出大对象
jmap -histo <pid> | head -10

# 2. 分析heap dump
# 在MAT中查看Dominator Tree，找出占用内存最大的对象
```

**解决方案：**
- 优化算法，减少大对象创建
- 分批处理数据
- 增加堆内存大小
- 使用流式处理

#### 5.1.2 场景2：内存泄漏

**问题代码：**
```java
public class MemoryLeakOOM {
    private static List<Object> cache = new ArrayList<>();
    
    public void addToCache(Object obj) {
        // 只添加不清理，导致内存泄漏
        cache.add(obj);
    }
}
```

**排查方法：**
```bash
# 1. 对比不同时间的heap dump
# 2. 查看对象增长趋势
# 3. 分析GC Roots引用链
```

**解决方案：**
- 及时清理不用的对象
- 使用WeakReference
- 设置缓存过期策略
- 定期清理缓存

#### 5.1.3 场景3：集合类误用

**问题代码：**
```java
public class CollectionOOM {
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            map.put("key" + i, "value" + i);
        }
    }
}
```

**排查方法：**
```bash
# 查看HashMap相关对象
jmap -histo <pid> | grep HashMap

# 在MAT中分析HashMap的大小和内容
```

**解决方案：**
- 限制集合大小
- 使用合适的初始容量
- 定期清理集合
- 考虑使用外部存储

### 5.2 Metaspace OOM

#### 5.2.1 场景：动态类生成

**问题代码：**
```java
public class MetaspaceOOM {
    public static void main(String[] args) {
        while (true) {
            // 动态生成类，导致Metaspace溢出
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(Object.class);
            enhancer.setCallback(NoOp.INSTANCE);
            enhancer.create();
        }
    }
}
```

**排查方法：**
```bash
# 查看Metaspace使用情况
jstat -gc <pid>

# 查看类加载统计
jmap -clstats <pid>

# 查看类加载器信息
jcmd <pid> VM.classloader_stats
```

**解决方案：**
- 控制动态类生成
- 增加Metaspace大小：`-XX:MetaspaceSize=256m`
- 优化类加载器使用
- 避免类加载器泄漏

### 5.3 Direct Memory OOM

#### 5.3.1 场景：NIO操作

**问题代码：**
```java
public class DirectMemoryOOM {
    public static void main(String[] args) {
        List<ByteBuffer> buffers = new ArrayList<>();
        while (true) {
            // 分配直接内存但不释放
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
            buffers.add(buffer);
        }
    }
}
```

**排查方法：**
```bash
# 查看直接内存使用情况
jcmd <pid> VM.native_memory summary

# 监控NIO相关指标
# 使用JVM参数：-XX:NativeMemoryTracking=summary
```

**解决方案：**
- 及时释放DirectByteBuffer
- 设置直接内存大小：`-XX:MaxDirectMemorySize=512m`
- 使用对象池复用Buffer
- 监控直接内存使用情况

---

## 6. 实际案例分析

### 6.1 案例1：AI Agent项目SSE连接内存泄漏

#### 6.1.1 问题现象

```
# 错误日志
java.lang.OutOfMemoryError: Java heap space
    at java.util.concurrent.ConcurrentHashMap.putVal(ConcurrentHashMap.java:1011)
    at java.util.concurrent.ConcurrentHashMap.put(ConcurrentHashMap.java:1006)
    at org.example.ai_agent.component.SseResponseHandler.createSseEmitter(SseResponseHandler.java:45)
```

#### 6.1.2 排查过程

**1. 生成heap dump**
```bash
# 应用OOM时自动生成
# JVM参数：-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/
ls -la /tmp/java_pid*.hprof
```

**2. 使用MAT分析**
```
# 发现问题：
- ConcurrentHashMap占用大量内存（1.2GB）
- SseEmitter对象数量异常（50000+个）
- 大量超时但未清理的连接
```

**3. 代码分析**
```java
// 问题代码
@Component
public class SseResponseHandler {
    private final Map<String, SseEmitter> connections = new ConcurrentHashMap<>();
    
    public SseEmitter createSseEmitter(String conversationId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        connections.put(conversationId, emitter);
        
        // 问题：没有设置超时和异常回调清理
        return emitter;
    }
}
```

#### 6.1.3 解决方案

```java
// 修复后的代码
@Component
public class SseResponseHandler {
    private final Map<String, SseEmitter> connections = new ConcurrentHashMap<>();
    
    public SseEmitter createSseEmitter(String conversationId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        
        // 设置回调清理连接
        emitter.onCompletion(() -> {
            connections.remove(conversationId);
            log.info("SSE connection completed: {}", conversationId);
        });
        
        emitter.onTimeout(() -> {
            connections.remove(conversationId);
            log.warn("SSE connection timeout: {}", conversationId);
        });
        
        emitter.onError((ex) -> {
            connections.remove(conversationId);
            log.error("SSE connection error: {}", conversationId, ex);
        });
        
        connections.put(conversationId, emitter);
        return emitter;
    }
    
    // 添加定时清理任务
    @Scheduled(fixedRate = 300000) // 5分钟
    public void cleanupExpiredConnections() {
        int before = connections.size();
        connections.entrySet().removeIf(entry -> {
            // 清理逻辑
            return isExpired(entry.getValue());
        });
        int after = connections.size();
        log.info("Cleaned up {} expired SSE connections", before - after);
    }
}
```

#### 6.1.4 效果验证

```bash
# 1. 监控连接数量
jcmd <pid> GC.run_finalization
jmap -histo <pid> | grep SseEmitter

# 2. 监控内存使用
jstat -gc <pid> 5s

# 3. 应用监控
# 通过Actuator监控连接数
curl http://localhost:8080/actuator/metrics/sse.connections.active
```

### 6.2 案例2：Redis连接池耗尽导致的间接OOM

#### 6.2.1 问题现象

```
# 错误日志
Could not get a resource from the pool
    at redis.clients.jedis.util.Pool.getResource(Pool.java:53)
    at org.example.ai_agent.service.CacheService.cacheAiResponse(CacheService.java:67)

# 随后出现OOM
java.lang.OutOfMemoryError: Java heap space
```

#### 6.2.2 排查过程

**1. 分析heap dump**
```
# MAT分析结果：
- Thread对象大量堆积
- 大量阻塞在连接池获取上的线程
- 每个线程栈占用约1MB内存
```

**2. 检查连接池配置**
```properties
# 原配置（有问题）
spring.redis.jedis.pool.max-active=8
spring.redis.jedis.pool.max-wait=1000ms
spring.redis.jedis.pool.max-idle=8
spring.redis.jedis.pool.min-idle=0
```

**3. 监控连接池状态**
```java
@Component
public class RedisPoolMonitor {
    @Autowired
    private JedisPool jedisPool;
    
    @EventListener
    @Scheduled(fixedRate = 30000)
    public void monitorPool() {
        log.info("Redis pool - Active: {}, Idle: {}, Waiting: {}", 
            jedisPool.getNumActive(),
            jedisPool.getNumIdle(), 
            jedisPool.getNumWaiters());
    }
}
```

#### 6.2.3 解决方案

```properties
# 优化后的配置
spring.redis.jedis.pool.max-active=50
spring.redis.jedis.pool.max-wait=3000ms
spring.redis.jedis.pool.max-idle=20
spring.redis.jedis.pool.min-idle=5
spring.redis.jedis.pool.test-on-borrow=true
spring.redis.jedis.pool.test-while-idle=true
```

```java
// 添加连接池监控和告警
@Component
public class RedisHealthIndicator implements HealthIndicator {
    @Autowired
    private JedisPool jedisPool;
    
    @Override
    public Health health() {
        try {
            int active = jedisPool.getNumActive();
            int maxActive = jedisPool.getMaxTotal();
            
            if (active > maxActive * 0.8) {
                return Health.down()
                    .withDetail("redis-pool", "Pool usage too high: " + active + "/" + maxActive)
                    .build();
            }
            
            return Health.up()
                .withDetail("redis-pool", active + "/" + maxActive)
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

---

## 7. 预防措施

### 7.1 JVM参数优化

#### 7.1.1 内存相关参数

```bash
# 堆内存设置
-Xms2g                    # 初始堆大小
-Xmx4g                    # 最大堆大小
-XX:NewRatio=3            # 老年代与新生代比例
-XX:SurvivorRatio=8       # Eden与Survivor比例

# Metaspace设置
-XX:MetaspaceSize=256m    # 初始Metaspace大小
-XX:MaxMetaspaceSize=512m # 最大Metaspace大小

# 直接内存设置
-XX:MaxDirectMemorySize=1g

# OOM时自动dump
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/path/to/dumps/

# GC优化
-XX:+UseG1GC              # 使用G1垃圾收集器
-XX:MaxGCPauseMillis=200  # 最大GC暂停时间
-XX:G1HeapRegionSize=16m  # G1区域大小
```

#### 7.1.2 监控相关参数

```bash
# GC日志
-Xloggc:gc.log
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-XX:+PrintGCApplicationStoppedTime
-XX:+UseGCLogFileRotation
-XX:NumberOfGCLogFiles=5
-XX:GCLogFileSize=100M

# 内存跟踪
-XX:NativeMemoryTracking=summary

# JFR监控
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=60s,filename=app.jfr
```

### 7.2 代码层面预防

#### 7.2.1 对象生命周期管理

```java
// 1. 及时释放资源
public class ResourceManager {
    public void processFile(String filename) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filename);
            // 处理文件
        } catch (IOException e) {
            log.error("File processing error", e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    log.warn("Failed to close file", e);
                }
            }
        }
    }
    
    // 更好的方式：使用try-with-resources
    public void processFileV2(String filename) {
        try (FileInputStream fis = new FileInputStream(filename)) {
            // 处理文件
        } catch (IOException e) {
            log.error("File processing error", e);
        }
    }
}

// 2. 使用对象池
@Component
public class ByteBufferPool {
    private final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final int bufferSize = 1024 * 1024; // 1MB
    
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
        }
        buffer.clear();
        return buffer;
    }
    
    public void release(ByteBuffer buffer) {
        if (buffer != null && buffer.capacity() == bufferSize) {
            pool.offer(buffer);
        }
    }
}

// 3. 使用WeakReference避免内存泄漏
public class CacheManager {
    private final Map<String, WeakReference<Object>> cache = new ConcurrentHashMap<>();
    
    public void put(String key, Object value) {
        cache.put(key, new WeakReference<>(value));
    }
    
    public Object get(String key) {
        WeakReference<Object> ref = cache.get(key);
        if (ref != null) {
            Object value = ref.get();
            if (value == null) {
                cache.remove(key); // 清理已被GC的引用
            }
            return value;
        }
        return null;
    }
}
```

#### 7.2.2 集合类使用最佳实践

```java
// 1. 设置合适的初始容量
public class CollectionBestPractice {
    public Map<String, String> createMap(int expectedSize) {
        // 避免频繁扩容
        int initialCapacity = (int) (expectedSize / 0.75f) + 1;
        return new HashMap<>(initialCapacity);
    }
    
    // 2. 限制集合大小
    private final Map<String, String> limitedCache = new LinkedHashMap<String, String>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 1000; // 限制最大1000个元素
        }
    };
    
    // 3. 使用合适的集合类型
    public void chooseRightCollection() {
        // 频繁随机访问：ArrayList
        List<String> randomAccess = new ArrayList<>();
        
        // 频繁插入删除：LinkedList
        List<String> frequentModify = new LinkedList<>();
        
        // 不重复元素：HashSet
        Set<String> uniqueElements = new HashSet<>();
        
        // 有序不重复：TreeSet
        Set<String> sortedUnique = new TreeSet<>();
    }
}
```

### 7.3 监控和告警

#### 7.3.1 应用级监控

```java
// 1. 内存使用监控
@Component
public class MemoryMonitor {
    private final MeterRegistry meterRegistry;
    
    public MemoryMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initMemoryMetrics();
    }
    
    private void initMemoryMetrics() {
        Gauge.builder("jvm.memory.heap.used")
            .register(meterRegistry, this, MemoryMonitor::getHeapUsed);
            
        Gauge.builder("jvm.memory.heap.usage")
            .register(meterRegistry, this, MemoryMonitor::getHeapUsage);
    }
    
    private double getHeapUsed(MemoryMonitor monitor) {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
    
    private double getHeapUsage(MemoryMonitor monitor) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return (double) heap.getUsed() / heap.getMax();
    }
}

// 2. 自定义健康检查
@Component
public class MemoryHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double usage = (double) heap.getUsed() / heap.getMax();
        
        if (usage > 0.9) {
            return Health.down()
                .withDetail("heap.usage", String.format("%.2f%%", usage * 100))
                .withDetail("heap.used", heap.getUsed())
                .withDetail("heap.max", heap.getMax())
                .build();
        }
        
        return Health.up()
            .withDetail("heap.usage", String.format("%.2f%%", usage * 100))
            .build();
    }
}
```

#### 7.3.2 告警配置

```yaml
# Prometheus告警规则示例
groups:
  - name: jvm-memory
    rules:
      - alert: HighHeapUsage
        expr: jvm_memory_heap_usage > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High heap memory usage"
          description: "Heap usage is {{ $value | humanizePercentage }}"
          
      - alert: CriticalHeapUsage
        expr: jvm_memory_heap_usage > 0.9
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Critical heap memory usage"
          description: "Heap usage is {{ $value | humanizePercentage }}, OOM risk!"
```

### 7.4 定期维护

#### 7.4.1 定期检查脚本

```bash
#!/bin/bash
# memory_check.sh - 定期内存检查脚本

APP_PID=$(jps | grep YourApplication | awk '{print $1}')

if [ -z "$APP_PID" ]; then
    echo "Application not running"
    exit 1
fi

echo "=== Memory Check Report $(date) ==="

# 1. 堆内存使用情况
echo "\n1. Heap Memory Usage:"
jmap -heap $APP_PID | grep -E "(used|capacity|free)"

# 2. GC统计
echo "\n2. GC Statistics:"
jstat -gc $APP_PID

# 3. 类统计（前10）
echo "\n3. Top 10 Classes by Instance Count:"
jmap -histo $APP_PID | head -15

# 4. 检查是否需要告警
HEAP_USAGE=$(jstat -gc $APP_PID | tail -1 | awk '{print ($3+$4+$6+$8)/($1+$2)*100}')
if (( $(echo "$HEAP_USAGE > 80" | bc -l) )); then
    echo "\n⚠️  WARNING: Heap usage is ${HEAP_USAGE}%"
    # 发送告警
    curl -X POST "$WEBHOOK_URL" -d "{\"text\":\"High memory usage: ${HEAP_USAGE}%\"}"
fi

echo "\n=== End of Report ==="
```

#### 7.4.2 自动化清理任务

```java
@Component
public class MemoryMaintenanceTask {
    
    // 定期清理缓存
    @Scheduled(fixedRate = 3600000) // 1小时
    public void cleanupCache() {
        // 清理过期缓存
        cacheManager.evictExpiredEntries();
        
        // 手动触发GC（谨慎使用）
        if (shouldTriggerGC()) {
            System.gc();
            log.info("Manual GC triggered");
        }
    }
    
    // 定期检查内存使用情况
    @Scheduled(fixedRate = 300000) // 5分钟
    public void checkMemoryUsage() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double usage = (double) heap.getUsed() / heap.getMax();
        
        if (usage > 0.8) {
            log.warn("High memory usage detected: {}%", usage * 100);
            // 触发清理操作
            performEmergencyCleanup();
        }
    }
    
    private boolean shouldTriggerGC() {
        // 根据内存使用情况决定是否触发GC
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return (double) heap.getUsed() / heap.getMax() > 0.7;
    }
    
    private void performEmergencyCleanup() {
        // 紧急清理操作
        log.info("Performing emergency cleanup");
        
        // 清理各种缓存
        cacheManager.clear();
        
        // 清理临时对象
        tempObjectManager.cleanup();
        
        // 关闭不必要的连接
        connectionManager.closeIdleConnections();
    }
}
```

---

## 总结

本指南涵盖了Java OOM问题排查的完整流程，从工具使用到实际案例分析。关键要点：

1. **预防为主**：合理的JVM参数配置和代码规范
2. **及时发现**：完善的监控和告警机制
3. **快速定位**：熟练使用jmap、MAT等分析工具
4. **根本解决**：分析根因，从代码层面解决问题
5. **持续改进**：定期检查和优化，避免问题重复发生

记住，OOM问题的排查需要结合具体的业务场景和代码实现，本指南提供的是通用的方法论和工具使用技巧。在实际工作中，要根据具体情况灵活运用这些技术手段。