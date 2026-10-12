package com.mall.pro.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.common.util.JWTUtil;
import com.mall.pro.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class AuthInterceptor  implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if("OPTIONS".equals(request.getMethod())){
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            writeErrorResponse(response, 401, "请先登录（缺少身份令牌）");
            return false;
        }
        String token = authHeader.substring(7);
        Long userId = JWTUtil.parseUserId(token);
        if (userId == null) {
            writeErrorResponse(response, 401, "身份令牌已失效或非法，请重新登录");
            return false;
        }
        // 核心：绑定到当前请求线程的 ThreadLocal 上下文中
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束彻底截断清理，杜绝 Tomcat 线程池复用导致的身份串号与内存泄露
        UserContext.clear();
    }

    private void writeErrorResponse(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(code, msg);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }
}
