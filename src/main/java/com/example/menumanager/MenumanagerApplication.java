package com.example.menumanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MenumanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MenumanagerApplication.class, args);
	}

}
