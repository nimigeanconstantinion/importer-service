package com.example.importer.service;
import com.example.importer.model.MessageAction;
import com.example.importer.model.MessageEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MessagePublisherService {
  private final KafkaTemplate<String, MessageEvent> kafkaTemplate;
  private final String topic;

  public MessagePublisherService(KafkaTemplate<String, MessageEvent> kafkaTemplate,
                                 @Value("${app.kafka.topic}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  public MessageEvent publishCreate(Object payload) {
    String id = UUID.randomUUID().toString();
    MessageEvent event = new MessageEvent(id, MessageAction.CREATED, payload, Instant.now());
    kafkaTemplate.send(topic, id, event);
    return event;
  }

  public MessageEvent publishUpdate(String id, Object payload) {
    MessageEvent event = new MessageEvent(id, MessageAction.UPDATED, payload, Instant.now());
    kafkaTemplate.send(topic, id, event);
    return event;
  }

  public MessageEvent publishDelete(String id,Object payload) {
    MessageEvent event = new MessageEvent(id, MessageAction.DELETED, payload, Instant.now());
    kafkaTemplate.send(topic, id, event);
    return event;
  }
}
