package com.mall.pro.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;


@Configuration
public class RedisConfig {

    @Bean
    public DefaultRedisScript <Long> seckillDeductScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("lua/seckill_deduct.lua"));
        redisScript.setResultType(Long.class);
        return redisScript;
    }
}
