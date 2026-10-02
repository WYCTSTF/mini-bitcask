package io.github.wyctstf.minibitcask;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1：记录格式的契约测试。其中 {@link #binaryLayoutIsFrozen()} 把磁盘格式钉死——
 * 任何时候改动布局，这个测试必须第一个被有意识地更新。
 */
class RecordCodecTest {

    private static byte[] bytes(String s) {
        return s.getBytes();
    }

    @Test
    @DisplayName("布局冻结：HEADER_SIZE=24，字段按文档偏移排列")
    void binaryLayoutIsFrozen() {
        byte[] record = RecordCodec.encode(bytes("key"), bytes("value"), 42L);

        assertEquals(24, RecordCodec.HEADER_SIZE);
        assertEquals(24 + 3 + 5, record.length);

        ByteBuffer buf = ByteBuffer.wrap(record);
        buf.position(8);
        assertEquals(42L, buf.getLong(), "offset 8 应为 timestamp");
        assertEquals(3, buf.getInt(), "offset 16 应为 keyLen");
        assertEquals(5, buf.getInt(), "offset 20 应为 valLen");
    }

    @Test
    @DisplayName("roundtrip：encode → decode 还原 key/value/timestamp")
    void roundtrip() {
        byte[] key = bytes("user:42");
        byte[] value = bytes("hello bitcask");

        RecordCodec.ParsedRecord parsed = RecordCodec.decode(RecordCodec.encode(key, value, 1234L));

        assertFalse(parsed.tombstone());
        assertArrayEquals(key, parsed.key());
        assertArrayEquals(value, parsed.value());
        assertEquals(1234L, parsed.timestamp());
    }

    @Test
    @DisplayName("墓碑：value=null 编码，valLen=-1，解码后 tombstone=true")
    void tombstone() {
        byte[] key = bytes("gone");

        byte[] record = RecordCodec.encode(key, null, 7L);
        assertEquals(24 + key.length, record.length, "墓碑不含 value 部分");

        ByteBuffer buf = ByteBuffer.wrap(record);
        assertEquals(-1, buf.getInt(20), "offset 20 应为 valLen=-1");

        RecordCodec.ParsedRecord parsed = RecordCodec.decode(record);
        assertTrue(parsed.tombstone());
        assertNull(parsed.value());
        assertArrayEquals(key, parsed.key());
    }

    @Test
    @DisplayName("CRC 校验：payload 被翻转一个比特即拒绝")
    void rejectsCorruptedPayload() {
        byte[] record = RecordCodec.encode(bytes("k"), bytes("aaaaaaaa"), 1L);

        record[record.length - 1] ^= 0x01; // 翻转 value 末字节

        assertThrows(CorruptRecordException.class, () -> RecordCodec.decode(record));
    }

    @Test
    @DisplayName("torn write：字节不足以容纳声明的长度即拒绝")
    void rejectsTruncatedRecord() {
        byte[] full = RecordCodec.encode(bytes("k"), bytes("0123456789"), 1L);

        byte[] torn = new byte[full.length - 3]; // 模拟崩溃时只写了前半截
        System.arraycopy(full, 0, torn, 0, torn.length);

        assertThrows(CorruptRecordException.class, () -> RecordCodec.decode(torn));
    }

    @Test
    @DisplayName("非法输入：key 为 null 或空数组")
    void rejectsBadInput() {
        assertThrows(Exception.class, () -> RecordCodec.encode(null, bytes("v"), 1L));
        assertThrows(Exception.class, () -> RecordCodec.encode(new byte[0], bytes("v"), 1L));
        // 注意：encode(key, null, ts) 本身就是墓碑的编码方式，不能在这里算非法；
        // “put 不允许 null value”是在 BitcaskKv.put 层校验的
    }
}
