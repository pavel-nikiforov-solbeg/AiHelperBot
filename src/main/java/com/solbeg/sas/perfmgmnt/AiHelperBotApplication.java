package com.solbeg.sas.perfmgmnt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiHelperBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiHelperBotApplication.class, args);
    }
}
