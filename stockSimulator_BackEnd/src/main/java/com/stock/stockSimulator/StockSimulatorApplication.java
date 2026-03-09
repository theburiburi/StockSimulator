package com.stock.stockSimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockSimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockSimulatorApplication.class, args);
	}

}
