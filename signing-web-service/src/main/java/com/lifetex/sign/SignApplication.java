package com.lifetex.sign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.lifetex.sign",
        "domain.email"
})
public class SignApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignApplication.class, args);
    }
}
