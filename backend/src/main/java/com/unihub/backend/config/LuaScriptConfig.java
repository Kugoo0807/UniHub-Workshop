package com.unihub.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;

/**
 * Configuration for loading Redis Lua scripts.
 * This is separate from RedisConfig to ensure scripts are always available,
 * regardless of whether Redis is enabled or not.
 */
@Configuration
public class LuaScriptConfig {

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
