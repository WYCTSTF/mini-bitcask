package io.github.wyctstf.minibitcask;

/**
 * 持久化档位。三档对应同一个问题的三种回答："崩溃时你愿意丢多少？"
 *
 * <p>关键事实：{@code write()} 一返回数据就已进入 OS page cache，同进程内的读立刻可见；
 * fsync/force 只决定<strong>进程崩溃或断电后</strong>这些页是否真的落到了磁盘。
 * 所以三档不影响 put 之后 get 的可见性，只影响崩溃丢失窗口。</p>
 */
public enum SyncMode {
    /** 只 write 不 force：吞吐最高，崩溃可能丢失尾部若干条已确认写入。 */
    OS,

    /**
     * 后台线程每 syncIntervalMs 对活跃文件批量 force 一次：
     * 崩溃最多丢失最近一个时间窗口（默认 200ms）的写入。默认档位。
     */
    GROUP_COMMIT,

    /** 每条写入 force 后才返回：崩溃零丢失，吞吐显著下降。 */
    ALWAYS
}
