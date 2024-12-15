package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.func.Func;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author lr1descent
 * @version 1.0 2024-12-14
 */
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据用户提供的参数将key存储至redis中
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 根据用户提供的参数将逻辑过期的key存储至redis中
     * 目的是解决缓存击穿问题
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 将value与有效期包装成RedisData
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 将RedisData保存至redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透问题
     * @param id
     * @return
     */
    public <R, ID> R queryShopByThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID, R> doFallBack, Long time, TimeUnit unit) {
        // 1. 查询redis中是否存在该数据
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断缓存是否真命中
        if (StrUtil.isNotBlank(jsonStr)) {
            // 3. 如果命中，则直接返回数据
            return JSONUtil.toBean(jsonStr, type);
        }

        // 4. 判断缓存是否假命中（查询数据为空字符串）
        if (jsonStr.equals("")) {
            // 5. 如果数据是空字符串，说明数据库中不存在该数据，直接返回空值
            return null;
        }

        // 5. 如果缓存未命中，查询数据库中的数据
        R r = doFallBack.apply(id);

        // 6. 如果查询数据不存在于数据库，返回空值，并写入空字符串至redis中，防止缓存穿透
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            return null;
        }

        // 7. 如果查询数据存在于数据库，将数据写到redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);

        // 8. 返回查询到的数据
        return r;
    }

    /**
     * 通过互斥锁解决缓存击穿和缓存穿透问题
     * @param id
     * @return
     */
    public <R, ID> R queryShopByMutex(String keyPrefix, String lockPrefix, ID id, Class<R> type,
                                       Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        // 1. 判断缓存是否命中
        if (StrUtil.isNotBlank(jsonStr)) {
            // 2. 如果缓存真命中，直接返回缓存数据
            return JSONUtil.toBean(jsonStr, type);
        }

        // 3. 判断缓存是否假命中
        if ("".equals(jsonStr)) {
            // 4. 如果缓存假命中，说明数据库中不存在查询数据，直接返回空值
            return null;
        }

        // 5. 如果缓存未命中，重建缓存
        // 6. 重建缓存前，先获取互斥锁
        String lockKey = lockPrefix + id;
        Boolean lock = tryLock(lockKey);
        while (!lock) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            finally {
                lock = tryLock(lockKey);
            }
        }

        // 7. 获取到了互斥锁，判断缓存是否重建完毕
        jsonStr = stringRedisTemplate.opsForValue().get(key);

        // 8. 如果缓存重建完毕，直接返回缓存数据
        if (StrUtil.isNotBlank(jsonStr)) {
            deleteLock(lockKey);
            return JSONUtil.toBean(jsonStr, type);
        }

        // 9. 如果缓存为空字符串，说明数据库中不存在该数据，直接返回null值
        if (jsonStr != null) {
            deleteLock(lockKey);
            return null;
        }

        // 10. 如果缓存为空，重建缓存
        // 11. 查询数据库
        R r = dbFallback.apply(id);

        // 12. 如果数据库中不存在查询数据，写入空字符串至redis中，防止发生缓存穿透
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", time, unit);

            // 释放互斥锁
            deleteLock(lockKey);
            // 返回null值
            return null;
        }

        // 13. 如果数据库中存在该数据，写入查询数据至redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
        // 释放互斥锁
        deleteLock(lockKey);

        // 14. 返回查询到的数据
        return r;
    }

    /**
     * 通过逻辑过期解决缓存击穿问题
     * @param id
     * @return
     */
    public <R, ID> R queryShopByLogicalExpire(String keyPrefix, String lockPrefix, ID id, Class<R> type,
                                               Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 查询缓存中是否存在查询数据
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        // 2. 如果未命中缓存，直接返回空值
        if (StrUtil.isBlank(jsonStr)) {
            return null;
        }

        // 3. 如果命中缓存，判断该缓存是否过期
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        // 获取缓存数据和有效期
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 如果缓存数据未过期，直接返回缓存数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        // 5. 如果缓存数据过期，则重建缓存
        // 6. 在重建缓存前，获取互斥锁
        String lockKey = lockPrefix + id;
        Boolean lock = tryLock(lockKey);

        // 7. 如果获取失败，说明存在别的线程正在重建缓存，直接返回旧的缓存数据
        if (!lock) {
            return r;
        }

        // 8. 如果获取成功，需要二次判断缓存中是否存在查询数据
        jsonStr = stringRedisTemplate.opsForValue().get(key);

        // 9. 如果缓存命中，判断缓存数据是否过期
        if (StrUtil.isNotBlank(jsonStr)) {
            RedisData bean = JSONUtil.toBean(jsonStr, RedisData.class);
            LocalDateTime expireTime1 = bean.getExpireTime();

            // 如果未过期，直接返回商铺数据
            if (expireTime1.isAfter(LocalDateTime.now())) {
                return JSONUtil.toBean((JSONObject) bean.getData(), type);
            }

            // 如果过期，继续重建缓存
        }

        // 10. 如果缓存未命中，开启一个新的线程，这个新的线程负责重建缓存
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            // 查询数据库中的数据
            R r1 = dbFallback.apply(id);

            // 写入至redis中
            setWithLogicalExpire(key, r1, time, unit);
        });

        // 11. 返回旧的店铺数据
        return r;
    }

    /**
     * 获取互斥锁
     * @param lockKey
     * @return
     */
    private Boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().
                setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 返还互斥锁
     * @param lockKey
     */
    private void deleteLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

}
