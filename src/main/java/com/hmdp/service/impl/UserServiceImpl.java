package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送手机验证码
     * @param phone
     * @return
     */
    @Override
    public Result sendCode(String phone) {
        // 1. 校验手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 如果不合法，那么返回"手机号不合法"
            return Result.fail("手机号不合法");
        }
        // 如果合法，那么继续执行下一步操作
        // 2. 随机生成一个6位数的验证码
//        String code = RandomUtil.randomString(6);
        String code = RandomUtil.randomNumbers(6);

        // 打印日志
        log.info("生成验证码：{}", code);

        // 4. 将验证码存储至redis中
        String codeKey = RedisConstants.LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(codeKey, code);

        // 5. 设置验证码有效期
        stringRedisTemplate.expire(codeKey, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 6. 返回"发送成功"
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1. 判断电话号码是否合法
        String phone = loginForm.getPhone();

        if (phone == null || RegexUtils.isPhoneInvalid(phone)) {
            // 如果电话号码为空或者电话号码不合法
            return Result.fail("电话号码不合法");
        }

        // 2. 判断提交的验证码是否跟生成的验证码一致
        String codeKey = RedisConstants.LOGIN_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (!cacheCode.equals(loginForm.getCode())) {
            // 如果提交的验证码不等于系统生成的验证码
            return Result.fail("验证码错误");
        }

        // 3. 判断数据库是否存在该用户数据
        User user = query().eq("phone", phone).one();
        System.out.println("查询user:" + user);

        if (user == null) {
            // 如果用户不存在
            // 创建用户信息
            user = createUserWithPhone(phone);
        }

        // 生成token
        String token = UUID.randomUUID().toString();
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;

        // 为了避免敏感信息的泄露，这里定义一个UserDTO，只返回部分信息
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);

        // 4. 将userDTO对象转成hash存储至redis中
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );

        System.out.println("userDTO转换成hash为：" + map);

        stringRedisTemplate.opsForHash().putAll(tokenKey, map);

        // 5. 设置token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 6. 登录成功
        log.info("登录成功！");
        return Result.ok(token);
    }

    /**
     * 创建用户信息
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        /*
        设置用户属性
        用户名默认为user_+16位字符
        密码默认为123456
         */
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(16));
        user.setPassword("123456");

        // 插入user至数据库中
        save(user);

        return user;
    }
}
