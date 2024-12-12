package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session
        HttpSession session = request.getSession();

        // 2. 从session中获取user对象
        Object user = session.getAttribute("user");

        // 3. 判断user对象是否为null
        if (user == null) {
            // 如果user对象为空，说明当前session不含有用户登录信息，也就是说用户未登录
            // 拦截该请求
            return false;
        }

        // 4. 将用户信息添加至ThreadLocal中
        UserHolder.saveUser((UserDTO) user);

        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求处理完成后，将用户信息从ThreadLocal中移除
        UserHolder.removeUser();
    }
}
