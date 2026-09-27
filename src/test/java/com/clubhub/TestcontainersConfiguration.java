package com.clubhub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // Pin the major version: "latest" would silently change the DB under CI
        return new PostgreSQLContainer("postgres:18");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        // same image and KRaft mode (no ZooKeeper) as docker-compose
        return new KafkaContainer("apache/kafka:4.1.0");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:8").withExposedPorts(6379);
    }
}
