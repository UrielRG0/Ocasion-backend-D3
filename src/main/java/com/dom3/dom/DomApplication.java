package com.dom3.dom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class DomApplication {

	public static void main(String[] args) {
		SpringApplication.run(DomApplication.class, args);
	}

}
