package com.beanstalk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class BeanstalkApplication {
    public static void main(String[] args) {
        SpringApplication.run(BeanstalkApplication.class, args);
    }
}
