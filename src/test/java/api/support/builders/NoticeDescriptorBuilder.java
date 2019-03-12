package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class NoticeDescriptorBuilder extends JsonBuilder implements Builder {

  private final UUID templateId;
  private final String format;
  private final String eventType;

  public NoticeDescriptorBuilder() {
    this(UUID.randomUUID(), "Email", null);
  }

  public NoticeDescriptorBuilder(UUID templateId, String format, String eventType) {
    this.templateId = templateId;
    this.format = format;
    this.eventType = eventType;
  }

  public NoticeDescriptorBuilder withTemplateId(UUID templateId) {
    return new NoticeDescriptorBuilder(
      templateId,
      this.format,
      this.eventType
    );
  }

  public NoticeDescriptorBuilder withFormat(UUID templateId) {
    return new NoticeDescriptorBuilder(
      templateId,
      format,
      this.eventType
    );
  }

  public NoticeDescriptorBuilder withEventType(String eventType) {
    return new NoticeDescriptorBuilder(
      templateId,
      this.format,
      eventType
    );
  }

  public NoticeDescriptorBuilder withCheckOutEvent() {
    return new NoticeDescriptorBuilder(
      templateId,
      this.format,
      "Check out"
    );
  }

  public NoticeDescriptorBuilder withCheckInEvent() {
    return new NoticeDescriptorBuilder(
      templateId,
      this.format,
      "Check in"
    );
  }

  @Override
  public JsonObject create() {
    JsonObject noticeDescriptor = new JsonObject();

    JsonObject sendOptions = new JsonObject();
    put(sendOptions, "sendWhen", eventType);

    put(noticeDescriptor, "templateId", templateId);
    put(noticeDescriptor, "format", format);
    put(noticeDescriptor, "sendOptions", sendOptions);
    put(noticeDescriptor, "templateId", templateId);
    return noticeDescriptor;
  }
}
