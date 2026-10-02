package com.mall.pro.module.pay.enums;

import lombok.Getter;

@Getter
public enum PayStatus {
    INIT(0,"待支付"),
    SUCCESS(1,"支付成功"),
    FAILED(2,"支付失败");

    private final int code;
    private final String description;

    PayStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static PayStatus fromCode(int code) {
        for (PayStatus status : PayStatus.values()) {
            if (status.code == code){
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown pay status code :" + code);
    }


}
