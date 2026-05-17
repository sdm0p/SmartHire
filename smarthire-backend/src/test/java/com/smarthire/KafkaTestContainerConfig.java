package com.smarthire;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

@TestConfiguration
public class KafkaTestContainerConfig {

    @Bean
    public org.apache.kafka.clients.admin.NewTopic resumeScreeningTopic() {
        return TopicBuilder.name("resume-screening")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
