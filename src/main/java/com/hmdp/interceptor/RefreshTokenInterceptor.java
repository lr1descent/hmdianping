package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 该拦截器负责刷新用户的token
 * 只要用户发送请求给服务器，服务器就会刷新该用户的token
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    // 由于LoginInterceptor没有交给IoC容器管理，所以无法注入stringRedisTemplate
    // 使用构造器进行注入
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头中获取token
        String token = request.getHeader("authorization");

        // 2. 判断token是否为空
        if (token == null) {
            // 如果token为空，放行，请求进入到下一个拦截器
            return true;
        }

        // 3. 从redis中查询token，并获取user对象
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        // 3. 判断user对象是否为null
        if (userMap.isEmpty()) {
            // 如果user对象为空，说明当前session不含有用户登录信息，也就是说用户未登录
            // 放行，进入到下一个拦截器
            return true;
        }

        // 4. 将userMap对象转换成userDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 5. 将userDTO对象存储至ThreadLocal中，方面后续方法的访问
        UserHolder.saveUser(userDTO);

        // 6. 更新token的有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 7. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求处理完成后，将用户信息从ThreadLocal中移除
        UserHolder.removeUser();
    }
}
