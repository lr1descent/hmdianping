package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

/**
 * @author lr1descent
 * @version 1.0 2024-12-14
 */
@SpringBootTest
public class SaveShop2RedisTest {

    @Resource
    private ShopServiceImpl shopService;

    @Test
    public void testSaveShop2Redis() {
        shopService.saveShop2Redis(1L, 30L);
    }

    @Test
    public void testLocalDateTime() {
        System.out.println(LocalDateTime.now());
    }
}
