package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
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

        // 3. 将验证码存储至当前会话，也就是session中
        session.setAttribute("code", code);

        // 4. 返回"发送成功"
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 判断电话号码是否合法
        String phone = loginForm.getPhone();

        if (phone == null || RegexUtils.isPhoneInvalid(phone)) {
            // 如果电话号码为空或者电话号码不合法
            return Result.fail("电话号码不合法");
        }

        // 2. 判断提交的验证码是否跟生成的验证码一致
        if (!session.getAttribute("code").equals(loginForm.getCode())) {
            // 如果提交的验证码不等于系统生成的验证码
            return Result.fail("验证码错误");
        }

        // 登录成功
        log.info("登录成功！");

        // 3. 判断数据库是否存在该用户数据
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 如果用户不存在
            // 创建用户信息
            user = createUserWithPhone(phone);
        }

        // 为了避免敏感信息的泄露，这里定义一个UserDTO，只返回部分信息
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);

        // 将用户信息保存至session
        session.setAttribute("user", userDTO);
        return Result.ok();
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

        return user;
    }
}
