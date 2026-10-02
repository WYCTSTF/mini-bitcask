package io.github.wyctstf.minibitcask;

/**
 * 内存索引（keydir）的值：一个 key 的最新版本存在哪个文件的哪个位置。
 *
 * @param fileId    segment 文件编号（约定：目录内按创建顺序单调递增）
 * @param offset    该条记录<strong>整体</strong>在文件中的起始偏移（含 CRC 头）
 * @param valueSize value 字节数；墓碑时为 -1
 * @param timestamp 写入时间戳（毫秒），恢复时用于"后写者胜"
 */
public record ValueLocation(int fileId, long offset, int valueSize, long timestamp) {
}
