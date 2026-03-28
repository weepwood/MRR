package com.zjcxph.imgapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@EnableScheduling
@ConfigurationPropertiesScan
@MapperScan("com.zjcxph.imgapi.mapper")
@SpringBootApplication
public class ImageApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageApiApplication.class, args);
    }

}
