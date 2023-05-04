package api.support.builders;

import java.util.UUID;

import org.folio.circulation.domain.policy.Period;

import io.vertx.core.json.JsonObject;

public class NoticeConfigurationBuilder extends JsonBuilder implements Builder {

  private final UUID templateId;
  private final String format;
  private final String eventType;
  private final String timing;
  private final JsonObject timingPeriod;
  private final JsonObject recurringPeriod;
  private final Boolean sendInRealTime;

  public NoticeConfigurationBuilder() {
    this(UUID.randomUUID(), "Email", null, "Upon At", null, null, true);
  }

  public NoticeConfigurationBuilder(
    UUID templateId, String format, String eventType, String timing, JsonObject timingPeriod, JsonObject recurringPeriod, Boolean sendInRealTime) {
    this.templateId = templateId;
    this.format = format;
    this.eventType = eventType;
    this.timing = timing;
    this.timingPeriod = timingPeriod;
    this.recurringPeriod = recurringPeriod;
    this.sendInRealTime = sendInRealTime;
  }

  public NoticeConfigurationBuilder withTemplateId(UUID templateId) {
    return new NoticeConfigurationBuilder(
      templateId,
      this.format,
      this.eventType,
      this.timing,
      this.timingPeriod,
      this.recurringPeriod,
      this.sendInRealTime);
  }

  public NoticeConfigurationBuilder withEventType(String eventType) {
    return new NoticeConfigurationBuilder(
      this.templateId,
      this.format,
      eventType,
      this.timing,
      this.timingPeriod,
      this.recurringPeriod,
      this.sendInRealTime);
  }

  public NoticeConfigurationBuilder withCheckOutEvent() {
    return withEventType("Check out");
  }

  public NoticeConfigurationBuilder withRenewalEvent() {
    return withEventType("Renewed");
  }

  public NoticeConfigurationBuilder withManualDueDateChangeEvent() {
    return withEventType("Manual due date change");
  }

  public NoticeConfigurationBuilder withCheckInEvent() {
    return withEventType("Check in");
  }

  public NoticeConfigurationBuilder withDueDateEvent() {
    return withEventType("Due date");
  }

  public NoticeConfigurationBuilder withAvailableEvent() {
    return withEventType("Available");
  }

  public NoticeConfigurationBuilder withRequestExpirationEvent() {
    return withEventType("Request expiration");
  }

  public NoticeConfigurationBuilder withHoldShelfExpirationEvent() {
    return withEventType("Hold expiration");
  }

  public NoticeConfigurationBuilder withOverdueFineReturnedEvent() {
    return withEventType("Overdue fine returned");
  }

  public NoticeConfigurationBuilder withOverdueFineRenewedEvent() {
    return withEventType("Overdue fine renewed");
  }

  public NoticeConfigurationBuilder withAgedToLostEvent() {
    return withEventType("Aged to lost");
  }

  public NoticeConfigurationBuilder withAgedToLostReturnedEvent() {
    return withEventType("Aged to lost & item returned - fine adjusted");
  }

  public NoticeConfigurationBuilder withAgedToLostFineChargedEvent() {
    return withEventType("Aged to lost - fine charged");
  }

  public NoticeConfigurationBuilder withTiming(String timing, JsonObject timingPeriod) {
    return new NoticeConfigurationBuilder(
      templateId,
      this.format,
      this.eventType,
      timing,
      timingPeriod,
      this.recurringPeriod,
      this.sendInRealTime);
  }

  public NoticeConfigurationBuilder withBeforeTiming(Period timingPeriod) {
    return withTiming("Before", timingPeriod.asJson());
  }

  public NoticeConfigurationBuilder withUponAtTiming() {
    return withTiming("Upon At", null);
  }

  public NoticeConfigurationBuilder withAfterTiming(Period timingPeriod) {
    return withTiming("After", timingPeriod.asJson());
  }

  public NoticeConfigurationBuilder recurring(Period recurringPeriod) {
    return new NoticeConfigurationBuilder(
      templateId,
      this.format,
      this.eventType,
      this.timing,
      this.timingPeriod,
      recurringPeriod != null ? recurringPeriod.asJson() : null,
      this.sendInRealTime);
  }

  public NoticeConfigurationBuilder sendInRealTime(boolean sendInRealTime) {
    return new NoticeConfigurationBuilder(
      templateId,
      this.format,
      this.eventType,
      this.timing,
      this.timingPeriod,
      this.recurringPeriod,
      sendInRealTime);
  }

  @Override
  public JsonObject create() {
    JsonObject noticeConfiguration = new JsonObject();
    JsonObject sendOptions = new JsonObject();

    put(noticeConfiguration, "templateId", templateId);
    put(noticeConfiguration, "format", format);
    put(noticeConfiguration, "realTime", sendInRealTime);

    if (recurringPeriod != null) {
      put(noticeConfiguration, "frequency", "Recurring");
      put(sendOptions, "sendEvery", recurringPeriod);
    }

    put(sendOptions, "sendWhen", eventType);
    put(sendOptions, "sendHow", timing);
    put(sendOptions, "sendBy", timingPeriod);
    put(noticeConfiguration, "sendOptions", sendOptions);

    return noticeConfiguration;
  }
}
