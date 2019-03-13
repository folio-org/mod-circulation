package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class NoticeConfigurationBuilder extends JsonBuilder implements Builder {

  private final UUID templateId;
  private final String format;
  private final String eventType;

  public NoticeConfigurationBuilder() {
    this(UUID.randomUUID(), "Email", null);
  }

  public NoticeConfigurationBuilder(UUID templateId, String format, String eventType) {
    this.templateId = templateId;
    this.format = format;
    this.eventType = eventType;
  }

  public NoticeConfigurationBuilder withTemplateId(UUID templateId) {
    return new NoticeConfigurationBuilder(
      templateId,
      this.format,
      this.eventType
    );
  }

  public NoticeConfigurationBuilder withFormat(UUID templateId) {
    return new NoticeConfigurationBuilder(
      templateId,
      format,
      this.eventType
    );
  }

  public NoticeConfigurationBuilder withEventType(String eventType) {
    return new NoticeConfigurationBuilder(
      templateId,
      this.format,
      eventType
    );
  }

  public NoticeConfigurationBuilder withCheckOutEvent() {
    return new NoticeConfigurationBuilder(
      templateId,
      this.format,
      "Check out"
    );
  }

  public NoticeConfigurationBuilder withCheckInEvent() {
    return new NoticeConfigurationBuilder(
      templateId,
      this.format,
      "Check in"
    );
  }

  @Override
  public JsonObject create() {
    JsonObject noticeConfiguration = new JsonObject();

    JsonObject sendOptions = new JsonObject();
    put(sendOptions, "sendWhen", eventType);

    put(noticeConfiguration, "templateId", templateId);
    put(noticeConfiguration, "format", format);
    put(noticeConfiguration, "sendOptions", sendOptions);
    put(noticeConfiguration, "templateId", templateId);
    return noticeConfiguration;
  }
}
