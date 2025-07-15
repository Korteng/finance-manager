package ru.korteng.finance_manager;

import org.springframework.boot.SpringApplication;

public class TestFinanceManagerApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinanceManagerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
