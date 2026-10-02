package io.github.wyctstf.minibitcask;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * mini-bitcask 对外接口：一个"落盘的 HashMap"。
 *
 * <p>所有方法接受/返回字节数组，键值的编解码是调用方的事。
 * 语义约定：</p>
 * <ul>
 *   <li><b>可见性</b>：put 返回后，同进程的 get 立即可见（与 SyncMode 无关）。</li>
 *   <li><b>持久性</b>：由 SyncMode 决定崩溃丢失窗口，见 {@link SyncMode}。</li>
 *   <li><b>键必须全装进内存</b>：这是 bitcask 的设定而非缺陷——索引即内存哈希表。</li>
 * </ul>
 */
public interface BitcaskKv extends AutoCloseable {

    /**
     * 打开（或创建）位于 {@code dir} 的数据目录，执行崩溃恢复后返回可用实例。
     *
     * @throws io.github.wyctstf.minibitcask.CorruptRecordException 当只读 segment 中部出现不可恢复的损坏
     *         （恢复策略见 DESIGN.md：活跃文件残尾截断，只读 segment 跳过坏条目继续）
     */
    static BitcaskKv open(Path dir, BitcaskConfig config) {
        return new MiniBitcask(dir, config);
    }

    /** 追加一条记录并更新内存索引。key/value 均不允许为 null 或空 key。 */
    void put(byte[] key, byte[] value);

    /** 读取当前值。不存在时返回 empty。 */
    Optional<byte[]> get(byte[] key);

    /** 写入墓碑并从内存索引移除。删除不存在的 key 是合法空操作。 */
    void delete(byte[] key);

    /** 当前所有存活 key（恢复与写入的共同视图）。返回快照，顺序不保证。 */
    List<byte[]> keys();

    @Override
    void close();
}
