package org.folio.circulation.domain.notice;

import io.vertx.core.json.JsonObject;

public class PatronNotice {

  private String recipientId;

  private String deliveryChannel;

  private String templateId;

  private String outputFormat;

  private JsonObject context;

  public String getRecipientId() {
    return recipientId;
  }

  public PatronNotice setRecipientId(String recipientId) {
    this.recipientId = recipientId;
    return this;
  }

  public String getDeliveryChannel() {
    return deliveryChannel;
  }

  public PatronNotice setDeliveryChannel(String deliveryChannel) {
    this.deliveryChannel = deliveryChannel;
    return this;
  }

  public String getTemplateId() {
    return templateId;
  }

  public PatronNotice setTemplateId(String templateId) {
    this.templateId = templateId;
    return this;
  }

  public String getOutputFormat() {
    return outputFormat;
  }

  public PatronNotice setOutputFormat(String outputFormat) {
    this.outputFormat = outputFormat;
    return this;
  }

  public JsonObject getContext() {
    return context;
  }

  public PatronNotice setContext(JsonObject context) {
    this.context = context;
    return this;
  }
}
