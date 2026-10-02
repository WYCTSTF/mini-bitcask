package io.github.wyctstf.minibitcask;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 引擎本体。骨架已给出文件布局与生命周期，M1 只要求把"单文件写读"跑通；
 * 标注 TODO(M1) 的方法是你当前的主战场，TODO(M2)/(M3) 先别碰。
 *
 * <h2>文件布局约定（M3 之前可以先只用活跃文件）</h2>
 * <pre>
 * dir/
 *   0.bitcask      —— segment：fileId 即文件名（不含后缀）的数字，创建顺序单调递增
 *   1.bitcask
 *   ...
 *   N.bitcask.active —— 活跃文件（当前唯一可写的那个）
 * </pre>
 *
 * <h2>M1 目标：单文件、ALWAYS 模式、单线程</h2>
 * <ol>
 *   <li>open：目录不存在则创建；打开 {@code N.bitcask.active}（没有就新建，N 从 0 开始）。</li>
 *   <li>put：{@link RecordCodec#encode} → channel.write → 更新 keydir。
 *       keydir 的 value 用 {@link ValueLocation}：offset 是<strong>这条记录的起始位置</strong>，
 *       即 write 之前的文件长度（position）。写完按 SyncMode 决定是否 force。</li>
 *   <li>get：查 keydir → 预读 {@code valueSize} 字节（提示：valueSize 就在
 *       ValueLocation 里，不需要再解析头）→ {@link RecordCodec#decode} → 取 value。</li>
 *   <li>delete：encode 墓碑（value 传 null）→ 追加 → keydir.remove。</li>
 * </ol>
 *
 * <h2>给你埋的两颗雷（BasicKvTest 会替我检查你有没有踩）</h2>
 * <ul>
 *   <li><b>byte[] 的 equals/hashCode 是对象身份</b>：直接用
 *       {@code HashMap<byte[], ValueLocation>}，put(key) 之后 get(内容相同的另一个数组)
 *       会返回 empty。想想怎么让"内容相同"的 key 在 HashMap 眼里相等——
 *       提示：ByteBuffer.wrap(...) 的 equals/hashCode 比较的是内容。</li>
 *   <li><b>get 的读长度</b>：不要图省事读整个文件再解码，keydir 已经告诉你 valueSize 了。</li>
 * </ul>
 *
 * <h2>TODO(M2) 预告：恢复</h2>
 * <p>构造函数里 recover()：按 fileId 从小到大重放所有 segment（含 active），
 * 逐条 decode 更新 keydir（后写者胜，墓碑移除 key）；active 末尾的
 * {@link CorruptRecordException} → 截断到上一条完好边界（用
 * {@link FileChannel#truncate}）；只读 segment 中部坏一条 → 跳过该条继续
 * （记录长度自包含：HEADER_SIZE + keyLen + max(valLen,0)，坏在头则放弃该文件剩余部分）。</p>
 *
 * <h2>TODO(M3) 预告：轮转与 group commit</h2>
 * <p>写前检查 position + nextRecordSize &gt; maxSegmentBytes → rotate：
 * 关 active → 改名为 {@code N.bitcask} → 开新 active。GROUP_COMMIT 用单线程
 * ScheduledExecutor 定时对 active force(false)；close 时 flusher 停机并最终 force 一次。</p>
 */
final class MiniBitcask implements BitcaskKv {

    private final Path dir;
    private final BitcaskConfig config;

    /**
     * keydir：key → 最新值的位置。M1 阶段只有活跃文件，fileId 恒为其编号。
     * 注意上面"雷一"——这个 Map 的 key 类型需要你斟酌。
     */
    private final Map<Object, ValueLocation> keyDir = new HashMap<>();

    private FileChannel activeChannel;
    private int activeFileId;

    MiniBitcask(Path dir, BitcaskConfig config) {
        this.dir = dir;
        this.config = config;
        try {
            Files.createDirectories(dir);
            // TODO(M2): recover() 应当先于打开活跃文件
            openActiveFile();
        } catch (IOException e) {
            throw new UncheckedIOException("打开数据目录失败: " + dir, e);
        }
    }

    private void openActiveFile() throws IOException {
        // TODO(M2): activeFileId 的确定要与 recover() 的重放结果一致；
        // M1 阶段固定从 0.bitcask.active 开始即可
        this.activeFileId = 0;
        Path active = dir.resolve(activeFileId + ".bitcask.active");
        this.activeChannel = FileChannel.open(active,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        // TODO(M1): encode → write → keydir → 按 SyncMode 决定 force
        throw new UnsupportedOperationException("M1: put 由你实现");
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        // TODO(M1): 查 keydir → 按位读取 → decode
        throw new UnsupportedOperationException("M1: get 由你实现");
    }

    @Override
    public void delete(byte[] key) {
        // TODO(M1): 墓碑也是一条普通记录，走 put 的路径，只是 valLen = TOMBSTONE
        throw new UnsupportedOperationException("M1: delete 由你实现");
    }

    @Override
    public List<byte[]> keys() {
        // TODO(M1): 返回 keyDir 全部 key 的内容快照（注意别把内部包装对象直接泄出去）
        throw new UnsupportedOperationException("M1: keys 由你实现");
    }

    @Override
    public void close() {
        // TODO(M3): 停 flusher、最终 force；
        // M1 阶段：force 一次再关 channel 即可（try-with-resources 由调用方保证）
        try {
            if (activeChannel != null && activeChannel.isOpen()) {
                activeChannel.force(true);
                activeChannel.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** M1 不需要；M2 恢复、M4 stat 都会用到的目录扫描，先放个签名占位。 */
    private List<Path> listSegments() throws IOException {
        try (var stream = Files.list(dir)) {
            List<Path> out = new ArrayList<>();
            stream.filter(p -> p.getFileName().toString().endsWith(".bitcask")
                            || p.getFileName().toString().endsWith(".bitcask.active"))
                    .sorted()
                    .forEach(out::add);
            return out;
        }
    }
}
