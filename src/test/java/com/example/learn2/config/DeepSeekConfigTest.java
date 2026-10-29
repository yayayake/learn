package com.example.learn2.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekConfigTest {

    @Test
    void requestFactoryReadTimeoutFires() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2000);
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            } catch (Exception ignored) {
                // client already timed out and closed the connection
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(DeepSeekConfig.newRequestFactory(Duration.ofSeconds(5), Duration.ofMillis(100)))
                .build();

            long start = System.currentTimeMillis();
            assertThrows(ResourceAccessException.class,
                () -> client.get().uri("/slow").retrieve().body(String.class));
            assertTrue(System.currentTimeMillis() - start < 1500,
                "expected read timeout to fire quickly, but the request hung");
        } finally {
            server.stop(0);
        }
    }
}
