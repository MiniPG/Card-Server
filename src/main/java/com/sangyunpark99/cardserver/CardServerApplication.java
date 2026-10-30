package com.sangyunpark99.cardserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CardServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardServerApplication.class, args);
    }

}
