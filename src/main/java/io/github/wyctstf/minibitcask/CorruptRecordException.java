package io.github.wyctstf.minibitcask;

/** 记录损坏：CRC 不匹配、长度字段异常或数据不足（torn write 的残尾）。 */
public class CorruptRecordException extends RuntimeException {

    public CorruptRecordException(String message) {
        super(message);
    }

    public CorruptRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
