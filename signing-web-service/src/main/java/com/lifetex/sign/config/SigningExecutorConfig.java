package com.lifetex.sign.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class SigningExecutorConfig {
    @Value("${signing.multi.thread-pool-size:4}")
    private int threadPoolSize;

    @Bean(name = "signingExecutor")
    public ExecutorService signingExecutor() {
        return Executors.newFixedThreadPool(threadPoolSize);
    }
}
