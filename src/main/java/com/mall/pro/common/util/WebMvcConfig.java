package com.mall.pro.common.util;

import com.mall.pro.common.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 白名单排除：公开接口与第三方回调放行
                .excludePathPatterns(
                        "/api/program/**",           // 演出检索与详情公开访问
                        "/api/user/login",          // 登录接口
                        "/api/user/register",       // 注册接口
                        "/api/tickets/category/**",  // 游客公开查票
                        "/api/tickets/verify",          // 闸机硬件在线核验
                        "/api/tickets/verify-offline",  // 闸机硬件断网离线核验
                        "/api/seckill/preheat",     // 运营预热
                        "/api/pay/mock/**",         // 沙箱收银台
                        "/api/pay/notify",          // 支付回调
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }
}
