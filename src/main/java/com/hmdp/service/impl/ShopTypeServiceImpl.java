package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 查询店铺类型
     * @return
     */
    @Override
    public Result queryTypeList() {
        // 1. 查询redis中是否存在店铺类型
        List<String> list = stringRedisTemplate.opsForList()
                .range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);

        // 2. 如果存在，直接返回店铺类型
        if (!list.isEmpty()) {
            List<ShopType> result = new ArrayList<>();
            // 将店铺类型从List<String>转换成List<ShopType>
            for (String shopTypeString : list) {
                ShopType shopType = JSONUtil.toBean(shopTypeString, ShopType.class);
                result.add(shopType);
            }

            // 返回店铺类型数据
            return Result.ok(result);
        }

        // 3. 如果不存在，查找数据库中是否存在店铺类型
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        // 4. 如果不存在，返回"店铺类型不存在!"
        if (shopTypeList.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }

        // 5. 如果存在，将店铺类型存储至redis中
        // 将shopTypeList从List<ShopType>转换成List<String>
        List<String> shopTypeStringList = new ArrayList<>();
        for (ShopType shopType : shopTypeList) {
            String shopTypeString = JSONUtil.toJsonStr(shopType);
            shopTypeStringList.add(shopTypeString);
        }
        // 将店铺类型存储至redis的过程中，设置有效期
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, shopTypeStringList);
        stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY,
                RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.HOURS);

        // 6. 返回店铺类型
        return Result.ok(shopTypeList);
    }
}
