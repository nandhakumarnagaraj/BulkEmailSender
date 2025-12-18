package com.bulkemail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BulkEmailApplication {

	public static void main(String[] args) {
		SpringApplication.run(BulkEmailApplication.class, args);
	}

}
