package com.mall.pro.common.util;

public class UserContext {
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    /**
     * 请求结束必须清除，防止 Tomcat 线程池复用导致的内存泄漏与用户身份串号！
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
