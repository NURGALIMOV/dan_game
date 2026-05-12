package com.dangame.detective;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DetectiveApplication {
    public static void main(String[] args) {
        SpringApplication.run(DetectiveApplication.class, args);
    }
}
