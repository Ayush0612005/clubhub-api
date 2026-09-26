package com.clubhub;

import org.springframework.boot.SpringApplication;

public class TestClubhubApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(ClubhubApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
