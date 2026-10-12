package com.autocompany;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.autocompany.entity")
@EnableJpaRepositories(basePackages = "com.autocompany.repository")
public class AutoCompanyApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutoCompanyApplication.class, args);
    }
}