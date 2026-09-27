package com.mall.pro.common;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("[业务规则拦截] 状态码:{},原因:{}",e.getCode(),e.getMessage());
        return Result.error(e.getCode(),e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("「系统突发未捕获异常]异常堆栈：",e);
        return Result.error(500,"系统繁忙，请稍候重试");
    }
}
