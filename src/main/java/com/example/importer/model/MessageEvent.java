package com.example.importer.model;




import java.time.Instant;

public class MessageEvent {
  private String id;
  private MessageAction action;
  private Object payload;
  private Instant timestamp;

  public MessageEvent() {
  }

  public MessageEvent(String id, MessageAction action, Object payload, Instant timestamp) {
    this.id = id;
    this.action = action;
    this.payload = payload;
    this.timestamp = timestamp;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public MessageAction getAction() {
    return action;
  }

  public void setAction(MessageAction action) {
    this.action = action;
  }

  public Object getPayload() {
    return payload;
  }

  public void setPayload(Object payload) {
    this.payload = payload;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
