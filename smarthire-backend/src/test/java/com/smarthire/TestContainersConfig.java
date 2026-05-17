package com.smarthire;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class TestContainersConfig {

    public static PostgreSQLContainer<?> postgresContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"));
        container.withDatabaseName("smarthire_test");
        container.withUsername("test");
        container.withPassword("test");
        return container;
    }

    public static KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
                .withEmbeddedZookeeper();
    }
}
