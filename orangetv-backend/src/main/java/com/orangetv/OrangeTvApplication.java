package com.orangetv;

import com.orangetv.util.AppTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class OrangeTvApplication {

    public static void main(String[] args) {
        AppTime.initializeDefaultTimeZone();
        SpringApplication.run(OrangeTvApplication.class, args);
    }

}
