package com.zjcxph.imgapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ImageApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageApiApplication.class, args);
    }

}
