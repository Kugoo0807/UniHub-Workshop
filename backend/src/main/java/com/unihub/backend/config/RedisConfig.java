package com.unihub.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisConfig {

    @Value("${spring.data.redis.host:${spring.redis.host:localhost}}")
    private String redisHost;

    @Value("${spring.data.redis.port:${spring.redis.port:6379}}")
    private int redisPort;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(name = "seatDecrScript")
    public String seatDecrScript() throws Exception {
        ClassPathResource res = new ClassPathResource("redis/seat_decr.lua");
        byte[] data = FileCopyUtils.copyToByteArray(res.getInputStream());
        return new String(data, StandardCharsets.UTF_8);
    }

    @Bean(name = "seatReserveScript")
    public String seatReserveScript() throws Exception {
        ClassPathResource res = new ClassPathResource("redis/seat_reserve.lua");
        byte[] data = FileCopyUtils.copyToByteArray(res.getInputStream());
        return new String(data, StandardCharsets.UTF_8);
    }
}
