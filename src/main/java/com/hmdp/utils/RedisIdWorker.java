package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static cn.hutool.core.date.DateUtil.now;

/**
 * 全局Id生成器
 * 生成的Id具有唯一性，安全性等特点
 * @author lr1descent
 * @version 1.0 2024-12-15
 */
@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 2000年1月1号0点0时0秒的时间戳
    private static final long BEGIN_TIMESTAMP = 946684800L;

    // 序列号的位数
    private static final int BITS = 32;

    // 调用该方法获取全局唯一序列号
    public long increment(String keyPrefix) {
        // 为了保证Id的安全性，前缀使用当前的时间戳，后缀使用序列号
        // 1. 获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = epochSecond - BEGIN_TIMESTAMP;

        // 2. 通过redis获取序列号
        // key采用"icr:" + 业务名称 + 年月日
        // 采用年月日可以统计每天每月每年生成的序列数量
        // 采用业务名称可以区分不同业务中的id
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "icr:" + keyPrefix + date;
        long count = stringRedisTemplate.opsForValue().increment(key);

        // 3. 将时间戳与序列号拼接在一起
        return timestamp << BITS | count;
    }
}
