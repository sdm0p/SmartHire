package com.smarthire.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeEventProducer {

    private static final String TOPIC = "resume-screening";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendResumeEvent(Map<String, Object> event) {
        String candidateId = String.valueOf(event.get("candidate_id"));
        kafkaTemplate.send(TOPIC, candidateId, event);
        log.info("Published resume event for candidate {} to topic {}", candidateId, TOPIC);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Flushing and closing Kafka producer");
        kafkaTemplate.flush();
    }
}
