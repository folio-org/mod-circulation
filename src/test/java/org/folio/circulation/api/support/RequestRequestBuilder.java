package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;

public class RequestRequestBuilder implements Builder {
  private final UUID id;
  private final String requestType;
  private final DateTime requestDate;
  private final UUID itemId;
  private final UUID requesterId;
  private final String fulilmentPreference;
  private final LocalDate requestExpirationDate;
  private final LocalDate holdShelfExpirationDate;

  public RequestRequestBuilder() {
    this(UUID.randomUUID(),
      "Hold",
      new DateTime(2017, 7, 15, 9, 35, 27, DateTimeZone.UTC),
      UUID.randomUUID(),
      UUID.randomUUID(),
      "Hold Shelf",
      null,
      null);
  }

  private RequestRequestBuilder(
    UUID id,
    String requestType,
    DateTime requestDate,
    UUID itemId,
    UUID requesterId,
    String fulfilmentPreference,
    LocalDate requestExpirationDate, LocalDate holdShelfExpirationDate) {

    this.id = id;
    this.requestType = requestType;
    this.requestDate = requestDate;
    this.itemId = itemId;
    this.requesterId = requesterId;
    this.fulilmentPreference = fulfilmentPreference;
    this.requestExpirationDate = requestExpirationDate;
    this.holdShelfExpirationDate = holdShelfExpirationDate;
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(this.id != null) {
      request.put("id", this.id.toString());
    }

    request.put("requestType", this.requestType);
    request.put("requestDate", formatDateTime(this.requestDate));
    request.put("itemId", this.itemId.toString());
    request.put("requesterId", this.requesterId.toString());
    request.put("fulfilmentPreference", this.fulilmentPreference);

    if(requestExpirationDate != null) {
      request.put("requestExpirationDate",
        formatDateOnly(this.requestExpirationDate));
    }

    if(holdShelfExpirationDate != null) {
      request.put("holdShelfExpirationDate",
        formatDateOnly(this.holdShelfExpirationDate));
    }

    return request;
  }

  public RequestRequestBuilder recall() {
    return withRequestType("Recall");
  }

  public RequestRequestBuilder hold() {
    return withRequestType("Hold");
  }

  public RequestRequestBuilder page() {
    return withRequestType("Page");
  }

  public RequestRequestBuilder withRequestType(String requestType) {
    return new RequestRequestBuilder(
      this.id,
      requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate);
  }

  public RequestRequestBuilder withId(UUID newId) {
    return new RequestRequestBuilder(
      newId,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate);
  }

  public RequestRequestBuilder withNoId() {
    return new RequestRequestBuilder(
      null,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate);
  }

  public RequestRequestBuilder withRequestDate(DateTime requestDate) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      requestDate,
      this.itemId,
      this.requesterId,
      this.fulilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate);
  }

  public RequestRequestBuilder withItemId(UUID itemId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      itemId,
      this.requesterId,
      this.fulilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate);
  }

  public RequestRequestBuilder withRequesterId(UUID requesterId) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      requesterId,
      this.fulilmentPreference,
      this.requestExpirationDate,
      this.holdShelfExpirationDate);
  }

  public RequestRequestBuilder fulfilToHoldShelf() {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      "Hold Shelf",
      this.requestExpirationDate,
      this.holdShelfExpirationDate);
  }

  public RequestRequestBuilder withRequestExpiration(LocalDate requestExpiration) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulilmentPreference,
      requestExpiration,
      this.holdShelfExpirationDate);
  }

  public RequestRequestBuilder withHoldShelfExpiration(LocalDate holdShelfExpiration) {
    return new RequestRequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulilmentPreference,
      this.requestExpirationDate,
      holdShelfExpiration);
  }

  private String formatDateTime(DateTime requestDate) {
    return requestDate.toString(ISODateTimeFormat.dateTime());
  }

  private String formatDateOnly(LocalDate date) {
    return date.toString(DateTimeFormat.forPattern("yyyy-MM-dd"));
  }

}
