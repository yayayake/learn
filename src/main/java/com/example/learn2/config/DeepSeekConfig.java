package com.example.learn2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class DeepSeekConfig {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public RestClient deepSeekRestClient(DeepSeekProperties properties) {
        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(newRequestFactory(CONNECT_TIMEOUT, READ_TIMEOUT))
            .build();
    }

    static SimpleClientHttpRequestFactory newRequestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
