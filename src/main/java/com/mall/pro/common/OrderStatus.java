package com.mall.pro.common;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
public enum OrderStatus {
    CREATED(0, "待支付"),
    PAID(1, "已支付"),
    SHIPPED(2, "已发货"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消"),
    REFUNDED(5, "已退款");

    private final int code;
    private final String description;

    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status code: " + code);
    }

    /**
     * 有限状态机（FSM）跃迁合法性矩阵
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) return false;

        return switch (this) {
            case CREATED -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPED || target == REFUNDED;
            case SHIPPED -> target == COMPLETED;
            // 终态不可逆
            case COMPLETED, CANCELLED, REFUNDED -> false;
        };
    }
}
