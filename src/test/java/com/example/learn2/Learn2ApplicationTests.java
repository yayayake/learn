package com.example.learn2;

import com.example.learn2.config.DeepSeekProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class Learn2ApplicationTests {
    @Autowired DeepSeekProperties properties;

    @Test
    void contextLoadsAndBindsProperties() {
        assertEquals("https://api.deepseek.com", properties.baseUrl());
        assertEquals("deepseek-chat", properties.model());
    }
}
