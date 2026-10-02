package io.github.wyctstf.minibitcask;

/**
 * 引擎配置。不可变对象，通过 builder 构造。
 *
 * <pre>{@code
 * BitcaskConfig cfg = BitcaskConfig.newBuilder()
 *         .syncMode(SyncMode.ALWAYS)
 *         .build();
 * }</pre>
 */
public final class BitcaskConfig {

    private final SyncMode syncMode;
    private final int syncIntervalMs;
    private final long maxSegmentBytes;

    private BitcaskConfig(Builder b) {
        this.syncMode = b.syncMode;
        this.syncIntervalMs = b.syncIntervalMs;
        this.maxSegmentBytes = b.maxSegmentBytes;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public SyncMode syncMode() {
        return syncMode;
    }

    /** 仅 GROUP_COMMIT 模式下生效：两次批量 fsync 的间隔。 */
    public int syncIntervalMs() {
        return syncIntervalMs;
    }

    /**
     * 单个 segment 文件的大小上限。活跃文件写到该上限即轮转为只读 segment，
     * 并开启新文件。单条记录允许超过该上限（写完即轮转）。
     */
    public long maxSegmentBytes() {
        return maxSegmentBytes;
    }

    public static final class Builder {
        private SyncMode syncMode = SyncMode.GROUP_COMMIT;
        private int syncIntervalMs = 200;
        private long maxSegmentBytes = 64L << 20; // 64 MiB

        public Builder syncMode(SyncMode mode) {
            this.syncMode = mode;
            return this;
        }

        public Builder syncIntervalMs(int ms) {
            if (ms <= 0) throw new IllegalArgumentException("syncIntervalMs must be > 0");
            this.syncIntervalMs = ms;
            return this;
        }

        public Builder maxSegmentBytes(long bytes) {
            if (bytes <= 0) throw new IllegalArgumentException("maxSegmentBytes must be > 0");
            this.maxSegmentBytes = bytes;
            return this;
        }

        public BitcaskConfig build() {
            return new BitcaskConfig(this);
        }
    }
}
