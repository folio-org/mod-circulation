package org.folio.circulation.domain;

import static org.folio.circulation.domain.RequestFulfilmentPreference.HOLD_SHELF;
import static org.folio.circulation.domain.RequestStatus.CLOSED_CANCELLED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_FILLED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_PICKUP_EXPIRED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_UNFILLED;
import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.domain.RequestStatus.OPEN_IN_TRANSIT;
import static org.folio.circulation.domain.RequestStatus.OPEN_NOT_YET_FILLED;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_ADDITIONAL_INFORMATION;
import static org.folio.circulation.domain.representations.RequestProperties.HOLD_SHELF_EXPIRATION_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.POSITION;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_EXPIRATION_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.STATUS;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

public class Request implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;
  private final Item item;
  private final User requester;
  private final User proxy;
  private final Loan loan;
  private final ServicePoint pickupServicePoint;

  public static final String REQUEST_TYPE = "requestType";

  private boolean changedPosition = false;

  public Request(
    JsonObject representation,
    Item item,
    User requester,
    User proxy,
    Loan loan,
    ServicePoint pickupServicePoint) {

    this.representation = representation;
    this.item = item;
    this.requester = requester;
    this.proxy = proxy;
    this.loan = loan;
    this.pickupServicePoint = pickupServicePoint;
  }

  public static Request from(JsonObject representation) {
    return new Request(representation, null, null, null, null, null);
  }

  Request withJsonRepresentation(JsonObject representation) {
    return new Request(representation,
      getItem(),
      getRequester(),
      getProxy(),
      getLoan(),
      getPickupServicePoint());
  }

  public JsonObject asJson() {
    return representation.copy();
  }

  boolean isFulfillable() {
    return getFulfilmentPreference() == HOLD_SHELF;
  }

  public boolean isOpen() {
    return isAwaitingPickup() || isNotYetFilled()|| isInTransit();
  }

  private boolean isInTransit(){
    return getStatus() == OPEN_IN_TRANSIT;
  }

  private boolean isNotYetFilled(){
    return getStatus() == OPEN_NOT_YET_FILLED;
  }

  boolean isCancelled() {
    return getStatus() == CLOSED_CANCELLED;
  }

  private boolean isFulfilled() {
    return getStatus() == CLOSED_FILLED;
  }

  private boolean isUnfilled() {
    return getStatus() == CLOSED_UNFILLED;
  }

  private boolean isPickupExpired() {
    return getStatus() == CLOSED_PICKUP_EXPIRED;
  }

  public boolean isClosed() {
    //Alternatively, check status contains "Closed"
    return isCancelled() || isFulfilled() || isUnfilled() || isPickupExpired();
  }

  boolean isAwaitingPickup() {
    return getStatus() == OPEN_AWAITING_PICKUP;
  }

  boolean isFor(User user) {
    return StringUtils.equals(getUserId(), user.getId());
  }

  @Override
  public String getItemId() {
    return representation.getString("itemId");
  }

  public Request withItem(Item newItem) {
    return new Request(representation, newItem, requester, proxy, loan,
      pickupServicePoint);
  }

  public Request withRequester(User newRequester) {
    return new Request(representation, item, newRequester, proxy, loan,
      pickupServicePoint);
  }

  public Request withProxy(User newProxy) {
    return new Request(representation, item, requester, newProxy, loan,
      pickupServicePoint);
  }

  public Request withLoan(Loan newLoan) {
    return new Request(representation, item, requester, proxy, newLoan,
      pickupServicePoint);
  }

  public Request withPickupServicePoint(ServicePoint newPickupServicePoint) {
    return new Request(representation, item, requester, proxy, loan,
      newPickupServicePoint);
  }

  @Override
  public String getUserId() {
    return representation.getString("requesterId");
  }

  @Override
  public String getProxyUserId() {
    return representation.getString("proxyUserId");
  }

  private String getFulfilmentPreferenceName() {
    return representation.getString("fulfilmentPreference");
  }

  public RequestFulfilmentPreference getFulfilmentPreference() {
    return RequestFulfilmentPreference.from(getFulfilmentPreferenceName());
  }

  public String getId() {
    return representation.getString("id");
  }

  public RequestType getRequestType() {
    return RequestType.from(representation.getString(REQUEST_TYPE));
  }

  Boolean allowedForItem() {
    return RequestTypeItemStatusWhiteList.canCreateRequestForItem(getItem().getStatus(), getRequestType());
  }

  String actionOnCreation() {
    return getRequestType().toLoanAction();
  }

  public RequestStatus getStatus() {
    return RequestStatus.from(representation.getString(STATUS));
  }

  void changeStatus(RequestStatus status) {
    //TODO: Check for null status
    status.writeTo(representation);
  }

  public Item getItem() {
    return item;
  }

  public Loan getLoan() {
    return loan;
  }

  public User getRequester() {
    return requester;
  }

  public JsonObject getRequesterFromRepresentation() {
    return representation.getJsonObject("requester");
  }

  public String getRequesterBarcode() {
    return getRequesterFromRepresentation().getString("barcode", StringUtils.EMPTY);
  }

  public User getProxy() {
    return proxy;
  }

  public String getPickupServicePointId() {
    return representation.getString("pickupServicePointId");
  }

  public ServicePoint getPickupServicePoint() {
    return pickupServicePoint;
  }

  Request changePosition(Integer newPosition) {
    if (!Objects.equals(getPosition(), newPosition)) {
      write(representation, POSITION, newPosition);
      changedPosition = true;
    }

    return this;
  }

  void removePosition() {
    representation.remove(POSITION);
    changedPosition = true;
  }

  public Integer getPosition() {
    return getIntegerProperty(representation, POSITION, null);
  }

  boolean hasChangedPosition() {
    return changedPosition;
  }

  ItemStatus checkedInItemStatus() {
    return getFulfilmentPreference().toCheckedInItemStatus();
  }

  String getDeliveryAddressType() {
    return representation.getString("deliveryAddressTypeId");
  }

  Request changeHoldShelfExpirationDate(DateTime holdShelfExpirationDate) {
    write(representation, HOLD_SHELF_EXPIRATION_DATE,
      holdShelfExpirationDate);

    return this;
  }

  void removeHoldShelfExpirationDate() {
    representation.remove(HOLD_SHELF_EXPIRATION_DATE);
  }

  public DateTime getHoldShelfExpirationDate() {
    return getDateTimeProperty(representation, HOLD_SHELF_EXPIRATION_DATE);
  }

  public DateTime getRequestExpirationDate() {
    return getDateTimeProperty(representation, REQUEST_EXPIRATION_DATE);
  }

  public String getCancellationAdditionalInformation() {
    return getProperty(representation, CANCELLATION_ADDITIONAL_INFORMATION);
  }
}
