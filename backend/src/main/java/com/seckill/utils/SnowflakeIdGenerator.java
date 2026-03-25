package com.seckill.utils;

import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器（64位）
 * <p>
 * 结构：1位符号 + 41位时间戳 + 5位数据中心 + 5位机器 + 12位序列号
 * 每毫秒可生成 4096 个 ID，理论上可用约 69 年
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始时间戳（2024-01-01 00:00:00 UTC） */
    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS   = 5L;
    private static final long DATACENTER_BITS  = 5L;
    private static final long SEQUENCE_BITS    = 12L;

    private static final long MAX_WORKER_ID    = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER   = ~(-1L << DATACENTER_BITS);

    private static final long WORKER_SHIFT     = SEQUENCE_BITS;
    private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT  = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_BITS;

    private static final long SEQUENCE_MASK    = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;

    private long sequence   = 0L;
    private long lastStamp  = -1L;

    public SnowflakeIdGenerator() {
        this(1, 1);
    }

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 不能大于 " + MAX_WORKER_ID);
        }
        if (datacenterId > MAX_DATACENTER || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 不能大于 " + MAX_DATACENTER);
        }
        this.workerId     = workerId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastStamp) {
            throw new RuntimeException("时钟回拨，拒绝生成 ID，回拨量: " + (lastStamp - timestamp) + "ms");
        }

        if (timestamp == lastStamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastStamp);
            }
        } else {
            sequence = 0L;
        }

        lastStamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
