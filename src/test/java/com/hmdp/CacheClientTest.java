package com.hmdp;

import cn.hutool.cache.Cache;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * @author lr1descent
 * @version 1.0 2024-12-15
 */
@SpringBootTest
public class CacheClientTest {
    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Test
    public void setWithLogicalExpireTest() {
        Shop shop = shopService.query().eq("id", 1L).one();
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1L, shop, 30L, TimeUnit.SECONDS);
    }

}
