package com.example.learn2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class DeepSeekConfig {
    @Bean
    public RestClient deepSeekRestClient(DeepSeekProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
