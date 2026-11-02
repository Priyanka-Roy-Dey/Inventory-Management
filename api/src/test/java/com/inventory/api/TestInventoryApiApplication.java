package com.inventory.api;

import org.springframework.boot.SpringApplication;

public class TestInventoryApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(InventoryApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
