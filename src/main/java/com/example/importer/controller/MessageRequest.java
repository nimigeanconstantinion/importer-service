package com.example.importer.controller;

import jakarta.validation.constraints.NotNull;

public class MessageRequest {
  @NotNull
  private Object payload;

  public Object getPayload() {
    return payload;
  }

  public void setPayload(Object payload) {
    this.payload = payload;
  }
}
