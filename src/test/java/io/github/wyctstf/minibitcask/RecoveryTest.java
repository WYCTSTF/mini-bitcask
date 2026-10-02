package io.github.wyctstf.minibitcask;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2：崩溃恢复。核心命题只有一句话——
 * <b>内存索引是易失的，磁盘日志是事实的唯一来源，重启 = 从日志重放出现状。</b>
 *
 * <p>这些测试在 M1 阶段 @Disabled；实现 recover() 时去掉注解，让它们转绿。</p>
 */
@DisplayName("M2: 恢复")
@Disabled("M2 实现后启用")
class RecoveryTest {

    @TempDir
    Path dir;

    private static byte[] bytes(String s) {
        return s.getBytes();
    }

    private BitcaskKv open() {
        return BitcaskKv.open(dir, BitcaskConfig.newBuilder()
                .syncMode(SyncMode.ALWAYS).build());
    }

    @Test
    @DisplayName("重启后数据仍在：日志重放重建 keydir")
    void persistenceAcrossReopen() {
        try (BitcaskKv db = open()) {
            db.put(bytes("a"), bytes("1"));
            db.put(bytes("b"), bytes("2"));
        }

        try (BitcaskKv db = open()) {
            assertArrayEquals(bytes("1"), db.get(bytes("a")).orElseThrow());
            assertArrayEquals(bytes("2"), db.get(bytes("b")).orElseThrow());
        }
    }

    @Test
    @DisplayName("重启后仍以后写者胜：覆盖写的历史在日志里，重放序即真相序")
    void lastWriteWinsAfterReopen() {
        try (BitcaskKv db = open()) {
            db.put(bytes("k"), bytes("v1"));
            db.put(bytes("k"), bytes("v2"));
            db.put(bytes("k"), bytes("v3"));
        }

        try (BitcaskKv db = open()) {
            assertArrayEquals(bytes("v3"), db.get(bytes("k")).orElseThrow());
        }
    }

    @Test
    @DisplayName("墓碑跨重启生效")
    void tombstoneSurvivesReopen() {
        try (BitcaskKv db = open()) {
            db.put(bytes("k"), bytes("v"));
            db.delete(bytes("k"));
        }

        try (BitcaskKv db = open()) {
            assertTrue(db.get(bytes("k")).isEmpty());
        }
    }

    @Test
    @DisplayName("torn write：残尾被截断，完好的数据不受牵连，截断后仍可继续写入")
    void tornTailIsTruncatedAndWritable() throws Exception {
        try (BitcaskKv db = open()) {
            db.put(bytes("a"), bytes("1"));
            db.put(bytes("b"), bytes("2"));
        }

        // 模拟崩溃现场：进程在写第三条时死了，文件尾部留下半条记录
        byte[] victim = RecordCodec.encode(bytes("c"), bytes("3-long-value"), 999L);
        byte[] torn = Arrays.copyOf(victim, victim.length - 5);
        Files.write(dir.resolve("0.bitcask.active"), torn,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        try (BitcaskKv db = open()) {
            assertArrayEquals(bytes("1"), db.get(bytes("a")).orElseThrow());
            assertArrayEquals(bytes("2"), db.get(bytes("b")).orElseThrow());
            assertTrue(db.get(bytes("c")).isEmpty());

            // 截断之后引擎必须还能正常写入（文件长度回到了上一条完好边界）
            db.put(bytes("d"), bytes("4"));
            assertArrayEquals(bytes("4"), db.get(bytes("d")).orElseThrow());
        }
    }

    @Test
    @DisplayName("CRC 损坏：活跃文件最后一条中间翻转比特 → 视同 torn write 截断")
    void corruptedLastRecordIsDropped() throws Exception {
        try (BitcaskKv db = open()) {
            db.put(bytes("a"), bytes("1"));
            db.put(bytes("victim"), bytes("0123456789"));
        }

        Path active = dir.resolve("0.bitcask.active");
        byte[] all = Files.readAllBytes(active);
        // 翻转最后一条记录 payload 的一个字节（倒数第一个字节是 '9'）
        all[all.length - 1] ^= 0x01;
        Files.write(active, all, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        try (BitcaskKv db = open()) {
            assertArrayEquals(bytes("1"), db.get(bytes("a")).orElseThrow());
            assertTrue(db.get(bytes("victim")).isEmpty(), "CRC 坏掉的最后一条应当被丢弃");
        }
    }
}
