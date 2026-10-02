package io.github.wyctstf.minibitcask;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * 记录编解码器——磁盘格式是引擎里唯一"说了算"的东西，务必先读完本注释再动手。
 *
 * <h2>记录布局（大端序）</h2>
 * <pre>
 * 偏移  大小  字段
 * 0     4    crc32   —— 对 [8, 记录末尾) 全部字节（timestamp + keyLen + valLen + key + value）的校验
 * 8     8    timestamp（毫秒）
 * 16    4    keyLen  （字节数，> 0）
 * 20    4    valLen  （字节数；<b>-1 表示墓碑</b>，此时记录不含 value 部分）
 * 24    ..   key
 * 24+K  ..   value   （墓碑时不存在）
 * </pre>
 *
 * <p>{@link #HEADER_SIZE} = 24。crc 自身不参与校验（否则鸡生蛋）。</p>
 *
 * <h2>你的任务（M1）</h2>
 * <ul>
 *   <li>{@link #encode(byte[], byte[], long)}：按布局拼出完整记录，crc 由 {@link #computeCrc} 算。</li>
 *   <li>{@link #decode(byte[])}：crc 不匹配 → 抛 {@link CorruptRecordException}；
 *       字节数不足以放下声明的 keyLen/valLen → 同样抛（torn write 的残尾就是长这样）。</li>
 * </ul>
 *
 * <p>建议用 {@link ByteBuffer}（默认即大端）：4 个 putXxx/getXxx 就能搞定头，
 * 不要手写位移运算，ByteBuffer 写法本身就是生产代码的标准姿势。</p>
 */
public final class RecordCodec {

    /** 头部固定长度：crc(4) + timestamp(8) + keyLen(4) + valLen(4)。 */
    public static final int HEADER_SIZE = 4 + 8 + 4 + 4;

    /** valLen 取该值表示墓碑（删除标记）。 */
    public static final int TOMBSTONE = -1;

    private RecordCodec() {
    }

    /** 解码产物：tombstone 为 true 时 value 恒为 null。 */
    public record ParsedRecord(byte[] key, byte[] value, long timestamp, boolean tombstone) {
    }

    /**
     * 编码一条完整记录（含 CRC 头）。墓碑传 {@code value = null}。
     * key 不允许为 null/空，value 仅墓碑时允许 null。
     */
    public static byte[] encode(byte[] key, byte[] value, long timestamp) {
        // TODO(M1): 由你实现
        throw new UnsupportedOperationException("M1: RecordCodec.encode 由你实现");
    }

    /**
     * 解码并校验一条完整记录（恰好一条记录的全部字节，不多不少）。
     *
     * @throws CorruptRecordException CRC 不匹配、长度字段非法（keyLen<=0 等）
     *                                或字节数不足以容纳声明的长度
     */
    public static ParsedRecord decode(byte[] record) {
        // TODO(M1): 由你实现
        throw new UnsupportedOperationException("M1: RecordCodec.decode 由你实现");
    }

    /** 供 encode/decode 共用的 CRC32。入参是待校验的字节区间，返回 crc 值。 */
    public static int computeCrc(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return (int) crc.getValue();
    }
}
