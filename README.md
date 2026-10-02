# mini-bitcask

学习用 [bitcask](https://riak.com/assets/bitcask-intro.pdf) 风格 KV 存储引擎，Java 实现：**append-only 日志 + 全内存索引**。

> 设计文档见 [DESIGN.md](DESIGN.md)。进度与取舍以本文档为准。

## 它是什么

一个"落盘的 HashMap"：写入只追加到日志文件，内存里一张哈希表记录每个 key 的最新位置，读取 = 查内存 + 一次磁盘 seek。没有 B 树，没有页管理，但 WAL、CRC 校验、崩溃恢复、torn write、墓碑这些存储引擎的核心命题一个不少。

对应阅读：*Designing Data-Intensive Applications* §3.1（Hash Indexes）——那一节开篇讲的引擎就是 Bitcask。

## v1 特性

| 特性 | 状态 |
|---|---|
| append-only 写 + 内存索引 | M1 |
| CRC32 校验 / 崩溃恢复 / 残尾截断 | M2 |
| segment 轮转 / OS·GROUP_COMMIT·ALWAYS 三档 fsync | M3 |
| 交互式 CLI（含崩溃实验） | M4 |
| benchmark（vs SQLite-JDBC） | M5 |

## v1 明确不做

- **merge/compaction**：磁盘只涨不回收（v2 的头号目标，`stat` 会显示垃圾率提醒你）
- TTL、范围扫描、多进程文件锁、超过内存容量的 key 数量

这些不是"没来得及"，是**特性冻结**——每一条都是有意欠的账，写进文档才算数。

## 快速开始

```bash
mvn test          # 测试套件（红 = 下一个要实现的里程碑）
mvn -q compile    # 编译
```

## 里程碑

- [ ] **M1** 单文件 append + get（ALWAYS，单线程）
- [ ] **M2** keydir 重放恢复 + CRC + torn write 截断
- [ ] **M3** 轮转 + 后台 flusher + 三档 sync
- [ ] **M4** CLI REPL + `kill -9` 崩溃实验
- [ ] **M5** benchmark + 博文

每个里程碑一组提交，完成打 tag。

## License

[MIT](LICENSE)
