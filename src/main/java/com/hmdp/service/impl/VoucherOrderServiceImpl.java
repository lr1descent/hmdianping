package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 秒杀下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询秒杀券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 2. 如果当前还没进入秒杀时刻
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始！");
        }

        // 3. 如果当前秒杀时刻已经结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束！");
        }

        // 4. 如果当前库存不够
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("秒杀券库存不足！");
        }

        // 5. 判断当前用户是否已经购买过此类优惠券
        // 高并发场景下，可能有同一用户的多个线程同时判断count > 0，同时执行后面的下单程序
        // 为了防止同一用户多个线程同时订单，对当前用户的下单程序加锁
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 方法的原子性是靠代理对象实现的
            // 需要获取代理对象，使用代理对象调用方法
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.order(voucherId);
        }
    }

    /**
     * 秒杀优惠券下单
     * 为了保证每个用户只能下一次单，将下单模板封装成一个方法，在主方法中对同一用户多次调用该方法加锁
     * @param voucherId
     * @return
     */
    @Transactional
    public Result order(Long voucherId) {
        // 1. 判断当前用户是否已经购买过此类优惠券
        Long userId = UserHolder.getUser().getId();
        int count = Math.toIntExact(query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count());

        // 2. 如果当前用户已经购买过此类优惠券
        if (count > 0) {
            return Result.fail("您已经购买过优惠券！");
        }

        // 如果用户没有购买过此类优惠券，可以继续购买
        // 3. 更新库存
        // 此处添加乐观锁来解决超卖问题
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").eq("voucher_id", voucherId)
                .gt("stock", 0).update();

        // 4. 如果更新失败，返回错误信息
        if (!success) {
            return Result.fail("库存不足！");
        }

        // 5. 如果更新成功，新增订单
        VoucherOrder voucherOrder = new VoucherOrder();

        // 6. 填写订单信息，包括订单id，优惠券id，下单人id等
        voucherOrder.setId(redisIdWorker.increment("order:"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());

        // 7. 插入订单至数据库中
        save(voucherOrder);

        // 8. 下单成功，返回订单id
        return Result.ok(voucherOrder.getId());
    }
}
