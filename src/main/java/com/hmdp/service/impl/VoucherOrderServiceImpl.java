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
import org.springframework.stereotype.Service;

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

        // 5. 更新库存
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").eq("voucher_id", voucherId).update();

        // 6. 如果更新失败，返回错误信息
        if (!success) {
            return Result.fail("库存不足！");
        }

        // 7. 如果更新成功，新增订单
        VoucherOrder voucherOrder = new VoucherOrder();

        // 8. 填写订单信息，包括订单id，优惠券id，下单人id等
        voucherOrder.setId(redisIdWorker.increment("order:"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());

        // 9. 插入订单至数据库中
        save(voucherOrder);

        // 10. 下单成功，返回订单id
        return Result.ok(voucherOrder.getId());
    }
}
