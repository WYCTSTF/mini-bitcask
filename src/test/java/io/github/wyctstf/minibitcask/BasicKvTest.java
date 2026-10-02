package io.github.wyctstf.minibitcask;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1：单文件、ALWAYS 模式、单进程内的基本读写删。
 * 重启后的持久性属于 M2（恢复），见 {@link RecoveryTest}。
 */
class BasicKvTest {

    @TempDir
    Path dir;

    private static byte[] bytes(String s) {
        return s.getBytes();
    }

    private static boolean contains(List<byte[]> keys, byte[] target) {
        return keys.stream().anyMatch(k -> Arrays.equals(k, target));
    }

    @Test
    @DisplayName("put → get")
    void putThenGet() {
        try (BitcaskKv db = BitcaskKv.open(dir, BitcaskConfig.newBuilder()
                .syncMode(SyncMode.ALWAYS).build())) {

            db.put(bytes("hello"), bytes("world"));
            assertArrayEquals(bytes("world"), db.get(bytes("hello")).orElseThrow());
            assertTrue(db.get(bytes("missing")).isEmpty());
        }
    }

    @Test
    @DisplayName("key 按“内容”而非“对象身份”匹配")
    void keyEqualityIsByContent() {
        try (BitcaskKv db = BitcaskKv.open(dir, BitcaskConfig.newBuilder()
                .syncMode(SyncMode.ALWAYS).build())) {

            byte[] key = {1, 2, 3, 4};
            db.put(key, bytes("v"));

            byte[] sameContentOtherObject = Arrays.copyOf(key, key.length);
            assertArrayEquals(bytes("v"), db.get(sameContentOtherObject).orElseThrow(),
                    "内容相同的两个数组必须命中同一条数据——想想 HashMap 的 key 类型");
        }
    }

    @Test
    @DisplayName("覆盖写：后写者胜")
    void overwriteWins() {
        try (BitcaskKv db = BitcaskKv.open(dir, BitcaskConfig.newBuilder()
                .syncMode(SyncMode.ALWAYS).build())) {

            db.put(bytes("k"), bytes("v1"));
            db.put(bytes("k"), bytes("v2"));
            assertArrayEquals(bytes("v2"), db.get(bytes("k")).orElseThrow());
        }
    }

    @Test
    @DisplayName("delete：墓碑后读不到；删除不存在的 key 是空操作")
    void delete() {
        try (BitcaskKv db = BitcaskKv.open(dir, BitcaskConfig.newBuilder()
                .syncMode(SyncMode.ALWAYS).build())) {

            db.put(bytes("k"), bytes("v"));
            db.delete(bytes("k"));
            assertTrue(db.get(bytes("k")).isEmpty());

            assertDoesNotThrow(() -> db.delete(bytes("never-existed")));
        }
    }

    @Test
    @DisplayName("keys()：返回存活 key 快照")
    void keysSnapshot() {
        try (BitcaskKv db = BitcaskKv.open(dir, BitcaskConfig.newBuilder()
                .syncMode(SyncMode.ALWAYS).build())) {

            db.put(bytes("a"), bytes("1"));
            db.put(bytes("b"), bytes("2"));
            db.put(bytes("c"), bytes("3"));
            db.delete(bytes("b"));

            List<byte[]> keys = db.keys();
            assertEquals(2, keys.size());
            assertTrue(contains(keys, bytes("a")));
            assertTrue(contains(keys, bytes("c")));
            assertFalse(contains(keys, bytes("b")));
        }
    }

    @Test
    @DisplayName("空 value 是合法数据，别和墓碑混淆")
    void emptyValueIsNotTombstone() {
        try (BitcaskKv db = BitcaskKv.open(dir, BitcaskConfig.newBuilder()
                .syncMode(SyncMode.ALWAYS).build())) {

            db.put(bytes("k"), new byte[0]);
            assertArrayEquals(new byte[0], db.get(bytes("k")).orElseThrow(),
                    "valLen=0 与 valLen=-1 是两回事");
        }
    }
}
