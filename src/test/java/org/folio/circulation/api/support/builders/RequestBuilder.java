package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;

public class RequestBuilder implements Builder {

  public static final String OPEN_NOT_YET_FILLED = "Open - Not yet filled";
  public static final String OPEN_AWAITING_PICKUP = "Open - Awaiting pickup";
  public static final String CLOSED_FILLED = "Closed - Filled";

  private final UUID id;
  private final String requestType;
  private final DateTime requestDate;
  private final UUID itemId;
  private final UUID requesterId;
  private final String fulfilmentPreference;
  private final UUID deliveryAddressTypeId;
  private final LocalDate requestExpirationDate;
  private final LocalDate holdShelfExpirationDate;
  private final ItemSummary itemSummary;
  private final PatronSummary requesterSummary;
  private final String status;
  private final UUID proxyUserId;

  public RequestBuilder() {
    this(UUID.randomUUID(),
      "Hold",
      new DateTime(2017, 7, 15, 9, 35, 27, DateTimeZone.UTC),
      UUID.randomUUID(),
      UUID.randomUUID(),
      "Hold Shelf",
      null,
      null,
      null,
      null,
      null,
      null,
      null);
  }

  public RequestBuilder(
    UUID id,
    String requestType,
    DateTime requestDate,
    UUID itemId,
    UUID requesterId,
    String fulfilmentPreference,
    UUID deliveryAddressTypeId,
    LocalDate requestExpirationDate,
    LocalDate holdShelfExpirationDate,
    ItemSummary itemSummary,
    PatronSummary requesterSummary,
    String status,
    UUID proxyUserId) {

    this.id = id;
    this.requestType = requestType;
    this.requestDate = requestDate;
    this.itemId = itemId;
    this.requesterId = requesterId;
    this.fulfilmentPreference = fulfilmentPreference;
    this.deliveryAddressTypeId = deliveryAddressTypeId;
    this.requestExpirationDate = requestExpirationDate;
    this.holdShelfExpirationDate = holdShelfExpirationDate;
    this.itemSummary = itemSummary;
    this.requesterSummary = requesterSummary;
    this.status = status;
    this.proxyUserId = proxyUserId;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(this.id != null) {
      request.put("id", this.id.toString());
    }

    request.put("requestType", this.requestType);
    request.put("requestDate", formatDateTime(this.requestDate));
    request.put("itemId", this.itemId.toString());
    request.put("requesterId", this.requesterId.toString());
    request.put("fulfilmentPreference", this.fulfilmentPreference);

    if(status != null) {
      request.put("status", this.status);
    }

    if(deliveryAddressTypeId != null) {
      request.put("deliveryAddressTypeId", this.deliveryAddressTypeId.toString());
    }

    if(requestExpirationDate != null) {
      request.put("requestExpirationDate",
        formatDateOnly(this.requestExpirationDate));
    }

    if(holdShelfExpirationDate != null) {
      request.put("holdShelfExpirationDate",
        formatDateOnly(this.holdShelfExpirationDate));
    }

    if(itemSummary != null) {
      request.put("item", new JsonObject()
        .put("title", itemSummary.title)
        .put("barcode", itemSummary.barcode));
    }

    if(requesterSummary != null) {
      JsonObject requester = new JsonObject()
        .put("lastName", requesterSummary.lastName)
        .put("firstName", requesterSummary.firstName);

      if(requesterSummary.middleName != null) {
        requester.put("middleName", requesterSummary.middleName);
      }

      requester.put("barcode", requesterSummary.barcode);

      request.put("requester", requester);
    }

    if(proxyUserId != null){
      request.put("proxyUserId", proxyUserId.toString());
    }

    return request;
  }

  public RequestBuilder withId(UUID newId) {
    return new RequestBuilder(
      newId,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder withNoId() {
    return new RequestBuilder(
      null,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder withRequestDate(DateTime requestDate) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder withRequestType(String requestType) {
    return new RequestBuilder(
      this.id,
      requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder hold() {
    return withRequestType("Hold");
  }

  public RequestBuilder page() {
    return withRequestType("Page");
  }

  public RequestBuilder recall() {
    return withRequestType("Recall");
  }

  public RequestBuilder withItemId(UUID itemId) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder forItem(IndividualResource item) {
    return withItemId(item.getId());
  }

  public RequestBuilder withRequesterId(UUID requesterId) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder by(IndividualResource requester) {
    return withRequesterId(requester.getId());
  }

  public RequestBuilder toHoldShelf() {
    return withFulfilmentPreference("Hold Shelf");
  }

  public RequestBuilder deliverToAddress(UUID addressTypeId) {
    return withFulfilmentPreference("Delivery")
      .withDeliveryAddressType(addressTypeId);
  }

  public RequestBuilder withFulfilmentPreference(String fulfilmentPreference) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder fulfilToHoldShelf() {
    return withFulfilmentPreference(
      "Hold Shelf");
  }

  public RequestBuilder withRequestExpiration(LocalDate requestExpiration) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      requestExpiration,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder withHoldShelfExpiration(LocalDate holdShelfExpiration) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      holdShelfExpiration,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder withDeliveryAddressType(UUID deliverAddressType) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      deliverAddressType,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      this.proxyUserId);
  }

  public RequestBuilder withStatus(String status) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      status,
      this.proxyUserId);
  }

  public RequestBuilder open() {
    return withStatus(OPEN_NOT_YET_FILLED);
  }

  public RequestBuilder withUserProxyId(UUID userProxyId) {
    return new RequestBuilder(
      this.id,
      this.requestType,
      this.requestDate,
      this.itemId,
      this.requesterId,
      this.fulfilmentPreference,
      this.deliveryAddressTypeId,
      this.requestExpirationDate,
      this.holdShelfExpirationDate,
      this.itemSummary,
      this.requesterSummary,
      this.status,
      userProxyId);
  }

  public Builder withNoStatus() {
    return withStatus(null);
  }

  public RequestBuilder fulfilled() {
    return withStatus(CLOSED_FILLED);
  }

  private String formatDateTime(DateTime requestDate) {
    return requestDate.toString(ISODateTimeFormat.dateTime());
  }

  private String formatDateOnly(LocalDate date) {
    return date.toString(DateTimeFormat.forPattern("yyyy-MM-dd"));
  }

  private class ItemSummary {
    public final String title;
    public final String barcode;

    public ItemSummary(String title, String barcode) {
      this.title = title;
      this.barcode = barcode;
    }
  }

  private class PatronSummary {
    public final String lastName;
    public final String firstName;
    public final String middleName;
    public final String barcode;

    public PatronSummary(String lastName, String firstName, String middleName, String barcode) {
      this.lastName = lastName;
      this.firstName = firstName;
      this.middleName = middleName;
      this.barcode = barcode;
    }
  }
}
