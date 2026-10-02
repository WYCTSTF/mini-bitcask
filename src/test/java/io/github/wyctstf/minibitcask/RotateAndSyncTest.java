package io.github.wyctstf.minibitcask;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M3：segment 轮转 + group commit。
 *
 * <p>诚实声明：GROUP_COMMIT 的"崩溃最多丢一个时间窗口"这个性质，在单进程内的
 * 单元测试里<strong>无法真正验证</strong>——同进程读永远能看到 page cache，fsync
 * 与否对它不可见。真正的掉电验证是 M4 里 CLI 配合 kill -9 的手工实验
 * （这也是一种工程判断力：知道哪些性质只能靠集成/手工验证）。
 * 这里能自动化验证的是轮转的正确性，以及恢复对多 segment 的处理。</p>
 */
@DisplayName("M3: 轮转与 sync 模式")
@Disabled("M3 实现后启用")
class RotateAndSyncTest {

    @TempDir
    Path dir;

    private static byte[] bytes(String s) {
        return s.getBytes();
    }

    @Test
    @DisplayName("写满 maxSegmentBytes 即轮转，旧 segment 变只读，数据不丢")
    void rotatesAndKeepsEverythingReadable() {
        BitcaskConfig cfg = BitcaskConfig.newBuilder()
                .syncMode(SyncMode.ALWAYS)
                .maxSegmentBytes(256) // 故意调小，几条记录就触发轮转
                .build();

        try (BitcaskKv db = BitcaskKv.open(dir, cfg)) {
            for (int i = 0; i < 50; i++) {
                db.put(bytes("key-" + i), bytes("value-" + i));
            }
        }

        try {
            long segmentCount = Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".bitcask"))
                    .count();
            assertTrue(segmentCount >= 2, "50 条记录在 256B 限制下必须产生多个 segment");
        } catch (Exception e) {
            fail(e);
        }

        try (BitcaskKv db = BitcaskKv.open(dir, cfg)) {
            for (int i = 0; i < 50; i++) {
                assertArrayEquals(bytes("value-" + i), db.get(bytes("key-" + i)).orElseThrow(),
                        "跨 segment 的 key 丢失：" + i);
            }
        }
    }
}
