package com.mall.pro.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
   private final Integer code;
   public BusinessException(Integer code, String message) {
       super(message);
       this.code = 400;
   }

   public BusinessException(Integer code, String message, Throwable cause) {
       super(message, cause);
       this.code = code;
   }
}
