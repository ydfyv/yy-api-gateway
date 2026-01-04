package com.yy.yyapigateway.config;

import com.yy.yyapigateway.filter.CustomGlobalFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 阿狸
 * @date 2026-01-04
 */
@Configuration
public class GatewayConfiguration {

    @Bean
    public GlobalFilter customFilter() {
        return new CustomGlobalFilter();
    }

}
