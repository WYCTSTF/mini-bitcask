# DESIGN

v1 需求与设计定稿。改动请以"先改本文档、再改代码"的顺序进行——文档与实现不一致时，先怀疑最近一次偷懒。

## 1. 定位

单机嵌入式 KV 存储引擎（Java 17+），append-only 日志结构、全内存索引。学习目标是存储引擎的核心命题，不是造生产级产品。

## 2. 接口

```java
BitcaskKv db = BitcaskKv.open(Path.of("/data/mini-bitcask"),
    BitcaskConfig.newBuilder()
        .syncMode(SyncMode.GROUP_COMMIT)   // OS | GROUP_COMMIT | ALWAYS
        .syncIntervalMs(200)
        .maxSegmentBytes(64 << 20)
        .build());

db.put(key, value);          // byte[] → byte[]
Optional<byte[]> v = db.get(key);
db.delete(key);              // 写墓碑
db.keys();                   // 存活 key 快照
db.close();                  // force + 关闭
```

语义约定：

- **可见性**：put 返回后同进程 get 立即可见，与 SyncMode 无关（write 进 page cache 即可见）。
- **持久性**：SyncMode 决定崩溃丢失窗口。
- **键必须全装进内存**：bitcask 设定。

## 3. 记录格式（大端序）

```
偏移  大小  字段
0     4    crc32   对 [8, 末尾) 全部字节
8     8    timestamp（毫秒）
16    4    keyLen
20    4    valLen  -1 = 墓碑（记录不含 value）
24    ..   key
24+K  ..   value
```

文件布局：

```
dir/
  0.bitcask          只读 segment（fileId = 文件名数字，创建序单调递增）
  1.bitcask
  N.bitcask.active   活跃文件（唯一可写）
```

写前检查 `position + nextRecordSize > maxSegmentBytes` → 轮转（close → rename → 新 active）。单条记录允许超过上限（写完即轮转）。

## 4. 三档 fsync

关键事实：`write()` 返回时数据已在 OS page cache，同进程读立即可见；fsync 只决定崩溃后是否还在磁盘上。

| 档位 | 行为 | 崩溃丢失窗口 |
|---|---|---|
| OS | 只 write | 尾部若干条 |
| GROUP_COMMIT（默认） | 后台线程每 200ms `force(false)` | 最近一个时间窗口 |
| ALWAYS | 每条写后 force | 0 |

并发注意：rotate 与 force、rotate 与写互斥，`ReadWriteLock` 一把即可（写多读少的锁——rotate 是低频事件，可以考虑写锁只在 rotate 时刻获取）。

## 5. 恢复

1. 列出目录内全部 segment，按 fileId 升序重放（active 最后）。
2. 逐条：读 header → 解析长度 → 读剩余字节 → 校验 CRC → 更新 keydir（后写者胜；墓碑移除 key）。
3. **active 末尾**残头/坏 CRC → torn write：截断到上一条完好边界，恢复继续可用。
4. **只读 segment 中部**坏一条 → 跳过该条继续（长度自包含：`HEADER_SIZE + keyLen + max(valLen, 0)`）；坏在头部无法定位下一条 → 放弃该文件剩余部分并抛 `CorruptRecordException`。
5. 恢复完成打印统计：扫描记录数、修复残尾字节数、keydir 大小。

墓碑无需"永久保留"机制：bitcask 是单机全序日志，重放顺序天然保证最后出现的墓碑生效（这与需要 merge 的引擎不同——那是 v2 的坑）。

## 6. CLI（M4）

```
open <dir>            打开/创建数据目录
put <key> <value>     写入
get <key>             读取
del <key>             删除
keys                  列出存活 key
stat                  segment 数 / 索引大小 / 磁盘占用 / 垃圾率
flush                 手动 force
bench <n>             内置微基准
quit
```

核心用途：**手工崩溃实验**——put 三条 → `kill -9` → 重开看恢复统计。GROUP_COMMIT 档下再对比"写入后立即 kill"与"写入后等 1s 再 kill"的丢失差异，亲眼看一次 fsync 窗口。

## 7. 测试策略

- 单元：格式契约（布局冻结测试）、读写删、覆盖写、墓碑、空 value。
- 恢复：重放、后写者胜、torn write 截断、CRC 损坏丢弃、截断后可继续写入。
- 轮转：小 segment 上限触发多文件，跨文件读完整性。
- 基准（M5）：顺序写 / 随机读，对照组 `ConcurrentHashMap`（内存上界）与 SQLite-JDBC（安全基线）。

已知不可自动化：GROUP_COMMIT 的真实丢失窗口（进程内读可见性不经过磁盘）。验证手段是 M4 手工实验——把"哪些性质只能集成验证"当作和代码同级的知识。

## 8. 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M1 | 单文件 append + get（ALWAYS，单线程） | RecordCodecTest / BasicKvTest 转绿 |
| M2 | keydir 重放恢复 + CRC + torn 截断 | RecoveryTest 转绿 |
| M3 | 轮转 + flusher + 三档 sync | RotateAndSyncTest 转绿 |
| M4 | CLI + 崩溃实验 | 手工 kill -9 实验记录（博文素材） |
| M5 | benchmark | 数字 + 博文 + tag v0.1.0 |
