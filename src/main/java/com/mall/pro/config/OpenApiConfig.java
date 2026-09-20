package com.mall.pro.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mallOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mall-Pro 垃圾小平台API文档")
                        .description("包含：Redis预热，动态地址获取，kafka削峰排队，异步结果轮询")
                        .version("v1.0.0")
                );
        }
    }

