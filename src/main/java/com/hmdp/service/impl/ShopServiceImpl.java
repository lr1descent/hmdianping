package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.hash.Hash;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import io.netty.handler.codec.json.JsonObjectDecoder;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.plaf.TableHeaderUI;
import javax.swing.text.html.Option;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据店铺id查询店铺
     * @param id
     * @return
     */
    @Override
    public Result queryShopById(Long id) {
//        // 使用互斥锁解决缓存击穿和缓存穿透问题
//        Shop shop = cacheClient.queryShopByMutex(
//                RedisConstants.CACHE_SHOP_KEY,
//                RedisConstants.LOCK_SHOP_KEY, id,
//                Shop.class, this::getById,
//                RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
//
//        if (shop == null) return Result.fail("店铺不存在");
//        return Result.ok(shop);

        Shop shop = cacheClient.queryShopByLogicalExpire(
        RedisConstants.CACHE_SHOP_KEY,
        RedisConstants.LOCK_SHOP_KEY, id,
        Shop.class, this::getById,
        RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);

        if (shop == null) return Result.fail("店铺不存在");
        return Result.ok(shop);
    }


    /**
     * 更新店铺
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 0. 判断shop的id是否为空
        if (shop.getId() == null) {
            return Result.fail("店铺的id不能为空！");
        }

        // 1. 更新数据库中的shop数据
        updateById(shop);

        // 2. 删除redis中的shop数据
        String id = String.valueOf(shop.getId());
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        // 3. 返回ok
        return Result.ok();
    }

    /**
     * 将商铺数据写入至缓存中
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1. 根据id查询商铺数据
        Shop shop = getById(id);
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;

        // 2. 将商品数据与有效期包装成RedisData
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3. 将RedisData写入到Redis中
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(redisData));
    }
//    /**
//     * 通过逻辑过期策略解决缓存击穿问题
//     * @param id
//     * @return
//     */
//    private Result queryShopByLogicalExpire(Long id) {
//        // 1. 根据id查询redis中是否存在商铺数据
//        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
//        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(shopKey);
//
//        // 这里是为了解决【缓存穿透】问题
//        // 2. 如果命中缓存，且expireTime为空字符串，说明数据库中不存在该商铺数据，直接返回"商铺不存在!"
//        if (map.get("expireTime").equals("")) {
//            return Result.fail("商铺不存在!");
//        }
//
//        // 3. 如果命中缓存且缓存不为空，需要判断是否缓存的店铺数据是否过期
//        if (!map.isEmpty()) {
//            RedisData redisData = BeanUtil.fillBeanWithMap(map, new RedisData(), false);
//            LocalDateTime expireTime = redisData.getExpireTime();
//
//            // 4. 如果未过期，直接返回店铺数据
//            if (expireTime.isAfter(LocalDateTime.now())) {
//                // 从redisData从取出店铺信息
//                Shop shop = (Shop) redisData.getData();
//                return Result.ok(shop);
//            }
//
//            // 5. 如果店铺数据已经过期，那么需要重建缓存
//            // 6. 在重建缓存之前，要先获得互斥锁
//            Boolean lock = tryLock(id);
//
//            // 7. 如果成功获取互斥锁，为了避免重复重建缓存，二次检查缓存是否命中
//            if (lock) {
//                map = stringRedisTemplate.opsForHash().entries(shopKey);
//                if (!map.isEmpty()) {
//                    // 8. 如果此时命中缓存，说明缓存已被重建，判断命中缓存的expireTime是否为空字符串
//                    RedisData redisData1 = BeanUtil.fillBeanWithMap(map, new RedisData(), false);
//                    LocalDateTime expireTime1 = redisData1.getExpireTime();
//                    if ("".equals(expireTime1)) {
//                        // 9. 如果expireTime为空字符串，说明查询的数据不存在于数据库中
//                        return Result.fail("店铺不存在!");
//                    }
//                    // 10. 如果expireTime不为空字符串，说明店铺数据已被写入redis中，直接返回店铺数据
//                    Shop shop = (Shop) redisData1.getData();
//                    return Result.ok(shop);
//                }
//                // 11. 如果缓存没有命中，根据逻辑过期策略，另外开启一个新的线程，由这个线程完成缓存重建
//                CACHE_REBUILD_EXECUTOR.submit(() -> {
//                    // 这个线程在进行缓存重建的过程中，要注意避免【缓存穿透】问题，如果查询数据库发现不存在店铺数据
//                    // 要向redis中写入一个空字符串来表示查询的店铺数据不存在于数据库，防止【缓存穿透】
//                    this.saveShop2Redis(id, 20L);
//
//                    // 线程完成缓存重建之后，释放互斥锁
//                    deleteLock(id);
//                });
//            }
//
//            // 12. 如果没有互斥锁，说明已经有别的线程在重建缓存
//            // 12. 不管有没有获得互斥锁，都返回旧的缓存数据
//            Shop shop = (Shop) redisData.getData();
//            return Result.ok(shop);
//        }
//
//        // -------------------------------------------------------------------------------------
//        // 既然程序进行到这一步了，说明未命中缓存
//
//        // 13. 如果没有命中缓存，重建缓存
//        // 14. 重建缓存前，获得互斥锁
//        Boolean lock = tryLock(id);
//        while (!lock) {
//            try {
//                Thread.sleep(50);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//            lock = tryLock(id);
//        }
//
//        // 15. 获得互斥锁后，二次检查缓存
//        map = stringRedisTemplate.opsForHash().entries(shopKey);
//
//        // 16. 如果此时命中缓存，检查是否过期
//        if (!map.isEmpty()) {
//            RedisData redisData1 = BeanUtil.fillBeanWithMap(map, new RedisData(), false);
//            LocalDateTime expireTime = redisData1.getExpireTime();
//
//            // 17. 如果未过期，直接返回缓存数据
//            if (expireTime.isAfter(LocalDateTime.now())) {
//                // 返回数据前返回互斥锁
//                deleteLock(id);
//                Shop shop = (Shop) redisData1.getData();
//                return Result.ok(shop);
//            }
//            // 18. 如果过期了，继续重建缓存
//        }
//
//        // 19. 未命中缓存或者缓存中的数据已经过期，需要重建缓存
//        // 20. 查询数据库中数据是否存在
//        Shop shop = getById(id);
//
//        // 21. 如果数据不存在
//        if (shop == null) {
//            // 将null值写入值redis中，防止缓存穿透
//            stringRedisTemplate.opsForHash().put(shopKey, "expireTime", "");
//
//            // 设置null值的TTL
//            stringRedisTemplate.expire(shopKey, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("该店铺不存在！");
//        }
//
//        // 22. 如果存在该数据，写入商铺数据到redis中
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(30));
//        Map<String, Object> map1 = BeanUtil.beanToMap(redisData);
//        stringRedisTemplate.opsForHash().putAll(shopKey, map1);
//
//        // 20. 返回店铺
//        return Result.ok(shop);
//    }
}
