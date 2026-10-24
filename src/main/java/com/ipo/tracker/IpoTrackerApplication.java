package com.ipo.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IpoTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IpoTrackerApplication.class, args);
    }
}
