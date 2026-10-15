package com.monograph.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MonographGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonographGatewayApplication.class, args);
    }
}
