# NOTES —— 开发日志

按场次追加。DESIGN.md 管"系统是什么"，本文档管"我学到哪了、踩过什么坑"。

## 2026-10-04 · 场次 1：M1 启动 + encode 概念补课

### 状态

- 骨架 `fcceb66` 已推送 GitHub（`WYCTSTF/mini-bitcask`），CI 已配置并在跑。
- 测试基线：`Tests run: 18, Errors: 11, Skipped: 6`
  - 11 红 = M1 验收线（RecordCodecTest 5 + BasicKvTest 6）
  - 6 禁用 = M2 恢复 ×5 + M3 轮转 ×1，实现对应里程碑后再启用
  - **1 假绿**：`RecordCodecTest.rejectsBadInput` 用的是 `assertThrows(Exception.class)`，
    现在抛 `UnsupportedOperationException` 也蒙混过关。实现时要抛对类型
    （`IllegalArgumentException`），别因为本来就绿就跳过这条。
- **开发环境迁移**：服务器 SSH 不跟手，Java 开发换到本地 WSL。
  WSL 需要自装 OpenJDK 21 + Maven 3.9（`sudo apt install openjdk-21-jdk maven`）。
  服务器只保留博客相关。

### M1 待办（按序）

1. ✅ 概念补课：CRC32、字节序 + `getBytes()`、buffer 与文件交互（见下文答疑）
2. ⬜ `RecordCodec.encode` → RecordCodecTest 前 3 个绿
3. ⬜ `RecordCodec.decode` → RecordCodecTest 后 3 个绿
4. ⬜ `MiniBitcask.put` / `get` / `delete` / `keys` → BasicKvTest 6 个绿
5. ⬜ 验收：`mvn test` Errors 11 → 0
6. ⬜ 彩蛋：拆解 `mvn test` 实际做了什么

### M1 是什么：一句话

**磁盘上只有一个只许追加的日志文件 + 内存里一张"key → 位置"索引（keydir）。**
写 = 往文件尾追加一条记录；读 = 查内存表，按位置去文件里抠字节。

### 一条数据的完整一生

`kv.put("name".getBytes(), "syh".getBytes())`，时间戳 `t = 1790985600000`：

```
encode 产出的 31 字节（偏移/大小/内容）：
0    4   crc32                对 [8..31) 这 23 字节算出
4    4   (填充，固定 0)        00 00 00 00  ← 别漏
8    8   timestamp            1790985600000
16   4   keyLen               4
20   4   valLen               3
24   4   key                  6E 61 6D 65  ("name")
28   3   value                73 79 68     ("syh")
```

- **put**：encode → 追加写入（写前 `channel.size()` 即本条 offset）→ keydir 更新：
  `"name" → ValueLocation(fileId=0, offset=0, valueSize=3, timestamp=t)`
- **get**：查 keydir 命中 → 从 `offset + 24 + keyLen` 处直接读 valueSize 字节（头不用重新解析）。
- **覆盖写**：不改历史，尾部追加新记录，keydir 指向新位置；旧记录成磁盘垃圾，
  回收是 v2 merge 的事——**磁盘脏无所谓，内存索引永远是真相**。
- **delete**：也是追加，只是 valLen = -1（墓碑），无 value 部分，共 24+keyLen 字节；
  然后 `keydir.remove`。

### 答疑记录

**Q1 CRC32 是什么、怎么算？**
纯函数：任意长字节流 → 固定 32 位指纹。本质是多项式除法取余（生成多项式 0x04C11DB7），
输入变 1 bit 指纹大变。用途：**防写坏**——写时对 `[8, 末尾)` 算 crc 存进记录头；
读时对同区间重算比对，不一致抛 `CorruptRecordException`（宁拒不返回脏数据）。
crc 不参与自身校验（区间从 8 开始的原因）。`computeCrc()` 骨架已提供，不用自己实现算法。

**Q2 字节序？（已纠正：x86 是小端，记反了）**

```
大端: 12 34 56 78   高位在前，人类书写顺序；网络字节序；磁盘格式约定用这个
小端: 78 56 34 12   低位在前；Intel/AMD x86 是这个（逆向 dump 内存看到的就是它）
```

对 encode 的实际影响 ≈ 0：Java `ByteBuffer` **默认大端**，`putInt/putLong` 出来直接符合格式。
坑在别处：手写位移拼字节（ICPC 习惯）容易拼成小端——所以 javadoc 要求用 ByteBuffer。
`getBytes()`：String 自带方法，`"name".getBytes(StandardCharsets.UTF_8)` → `[6E,61,6D,65]`；
反方向 `new String(bytes, UTF_8)`；charset 必须显式传（老 JDK 平台默认编码是乱码之源）。
附带坑：**Java 的 byte 有符号**（-128..127），打印时 `b & 0xFF`，存储无感。

**Q3 写到哪里？buffer 和文件什么关系？**
buffer = 会自己管理 position 游标的连续内存（对应 C 里手动维护的 ptr+len），不是线程池。
两层分开：

1. **内存拼装（encode 的全部职责，纯函数，不落盘）**：
   `ByteBuffer.allocate(n)` → `putInt/putLong/put(byte[])` → `array()` 取出。
2. **进文件（put 的事）**：`FileChannel`（= Java 版 fd/FILE*，已由骨架打开）：
   - `channel.size()` → 当前文件长度 = 追加位置（即 keydir 里的 offset）
   - `channel.write(ByteBuffer.wrap(bytes))` → 交给 OS 页缓存即返回，断电可能没到盘
   - `channel.force(true)` → 逼 OS 真正落盘（= fsync），慢但持久
   - ALWAYS vs GROUP_COMMIT 的权衡就在 write/force 的粒度上，M3 展开。

### 两颗雷（BasicKvTest 会查）

1. `HashMap<byte[], ...>` 不行——byte[] 的 hashCode 是对象身份，内容相同的两个数组互不命中。
   方向：用内容语义的包装类当 map key（`ByteBuffer.wrap` 的 equals/hashCode 比较内容），
   `keys()` 返回时再转回 `byte[]` 拷贝。
2. get 按位读 valueSize 字节，别读整个文件再解码——keydir 已经告诉你长度了。

### 建议顺序

encode → decode（先把 RecordCodecTest 清零，其余全依赖它）→ put → get → delete → keys。
BasicKvTest 现在 6 个全挂在 "put 由你实现" 上，encode 写完后会逐个亮起来，红哪个修哪个。
