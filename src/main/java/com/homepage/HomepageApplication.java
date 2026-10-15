package com.homepage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HomepageApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomepageApplication.class, args);
    }
}
