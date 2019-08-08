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
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_ID;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_NAME;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_PUBLIC_DESCRIPTION;
import static org.folio.circulation.domain.representations.RequestProperties.HOLD_SHELF_EXPIRATION_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_ID;
import static org.folio.circulation.domain.representations.RequestProperties.POSITION;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_EXPIRATION_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.domain.representations.RequestProperties.STATUS;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class Request implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject requestRepresentation;
  private final JsonObject cancellationReasonRepresentation;
  private final Item item;
  private final User requester;
  private final User proxy;
  private final Loan loan;
  private final ServicePoint pickupServicePoint;

  private boolean changedPosition = false;
  private Integer previousPosition;

  public Request(
    JsonObject requestRepresentation,
    JsonObject cancellationReasonRepresentation,
    Item item,
    User requester,
    User proxy,
    Loan loan,
    ServicePoint pickupServicePoint) {

    this.requestRepresentation = requestRepresentation;
    this.cancellationReasonRepresentation = cancellationReasonRepresentation;
    this.item = item;
    this.requester = requester;
    this.proxy = proxy;
    this.loan = loan;
    this.pickupServicePoint = pickupServicePoint;
  }

  public static Request from(JsonObject representation) {
    return new Request(representation, null, null, null, null, null, null);
  }

  Request withRequestJsonRepresentation(JsonObject representation) {
    return new Request(representation,
      cancellationReasonRepresentation,
      getItem(),
      getRequester(),
      getProxy(),
      getLoan(),
      getPickupServicePoint());
  }

  Request withCancellationReasonJsonRepresentation(JsonObject representation) {
    return new Request(requestRepresentation,
      representation,
      getItem(),
      getRequester(),
      getProxy(),
      getLoan(),
      getPickupServicePoint());
  }

  public JsonObject asJson() {
    return requestRepresentation.copy();
  }

  boolean isFulfillable() {
    return getFulfilmentPreference() == HOLD_SHELF;
  }

  public boolean isOpen() {
    return isAwaitingPickup() || isNotYetFilled() || isInTransit();
  }

  public boolean isNotDisplaceable() {
    return isAwaitingPickup() || isInTransit() || (isItemPaged() && isFirst());
  }

  private boolean isItemPaged() {
    return item != null && item.isPaged();
  }

  private boolean isFirst() {
    return hasPosition() && getPosition().equals(1);
  }

  private boolean isInTransit() {
    return getStatus() == OPEN_IN_TRANSIT;
  }

  private boolean isNotYetFilled() {
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
    return requestRepresentation.getString(ITEM_ID);
  }

  public Request withItem(Item newItem) {
    // NOTE: this is null in RequestsAPIUpdatingTests.replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist test 
    if (newItem.getItemId() != null) {
      requestRepresentation.put(ITEM_ID, newItem.getItemId());
    }
    return new Request(requestRepresentation, cancellationReasonRepresentation, newItem, requester, proxy,
      loan == null ? null : loan.withItem(newItem), pickupServicePoint);
  }

  public Request withRequester(User newRequester) {
    return new Request(requestRepresentation, cancellationReasonRepresentation, item, newRequester, proxy, loan,
      pickupServicePoint);
  }

  public Request withProxy(User newProxy) {
    return new Request(requestRepresentation, cancellationReasonRepresentation, item, requester, newProxy, loan,
      pickupServicePoint);
  }

  public Request withLoan(Loan newLoan) {
    return new Request(requestRepresentation, cancellationReasonRepresentation, item, requester, proxy, newLoan,
      pickupServicePoint);
  }

  public Request withPickupServicePoint(ServicePoint newPickupServicePoint) {
    return new Request(requestRepresentation, cancellationReasonRepresentation, item, requester, proxy, loan,
      newPickupServicePoint);
  }

  @Override
  public String getUserId() {
    return requestRepresentation.getString("requesterId");
  }

  @Override
  public String getProxyUserId() {
    return requestRepresentation.getString("proxyUserId");
  }

  private String getFulfilmentPreferenceName() {
    return requestRepresentation.getString("fulfilmentPreference");
  }

  public RequestFulfilmentPreference getFulfilmentPreference() {
    return RequestFulfilmentPreference.from(getFulfilmentPreferenceName());
  }

  public String getId() {
    return requestRepresentation.getString("id");
  }

  public RequestType getRequestType() {
    return RequestType.from(getProperty(requestRepresentation, REQUEST_TYPE));
  }

  Boolean allowedForItem() {
    return RequestTypeItemStatusWhiteList.canCreateRequestForItem(getItem().getStatus(), getRequestType());
  }

  String actionOnCreateOrUpdate() {
    return getRequestType().toLoanAction();
  }

  public RequestStatus getStatus() {
    return RequestStatus.from(requestRepresentation.getString(STATUS));
  }

  void changeStatus(RequestStatus status) {
    //TODO: Check for null status
    status.writeTo(requestRepresentation);
  }

  Request withRequestType(RequestType type) {
    requestRepresentation.put(REQUEST_TYPE, type.getValue());
    return this;
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
    return requestRepresentation.getJsonObject("requester");
  }

  public JsonObject getItemFromRepresentation() {
    return requestRepresentation.getJsonObject("item");
  }

  public String getRequesterBarcode() {
    return getRequesterFromRepresentation().getString("barcode", StringUtils.EMPTY);
  }

  public User getProxy() {
    return proxy;
  }

  public String getPickupServicePointId() {
    return requestRepresentation.getString("pickupServicePointId");
  }

  public ServicePoint getPickupServicePoint() {
    return pickupServicePoint;
  }

  void changePosition(Integer newPosition) {
    Integer prevPosition = getPosition();
    if (!Objects.equals(prevPosition, newPosition)) {
      previousPosition = prevPosition;
      write(requestRepresentation, POSITION, newPosition);
      changedPosition = true;
    }
  }

  void removePosition() {
    previousPosition = getPosition();
    requestRepresentation.remove(POSITION);
    changedPosition = true;
  }

  public Integer getPosition() {
    return getIntegerProperty(requestRepresentation, POSITION, null);
  }

  boolean hasPosition() {
    return getPosition() != null;
  }

  boolean hasChangedPosition() {
    return changedPosition;
  }

  Integer getPreviousPosition() {
    return previousPosition;
  }

  boolean hasPreviousPosition() {
    return getPreviousPosition() != null;
  }

  void freePreviousPosition() {
    previousPosition = null;
  }

  ItemStatus checkedInItemStatus() {
    return getFulfilmentPreference().toCheckedInItemStatus();
  }

  String getDeliveryAddressType() {
    return requestRepresentation.getString("deliveryAddressTypeId");
  }

  Request changeHoldShelfExpirationDate(DateTime holdShelfExpirationDate) {
    write(requestRepresentation, HOLD_SHELF_EXPIRATION_DATE,
      holdShelfExpirationDate);

    return this;
  }

  void removeHoldShelfExpirationDate() {
    requestRepresentation.remove(HOLD_SHELF_EXPIRATION_DATE);
  }
  
  public DateTime getRequestDate() {
    return getDateTimeProperty(requestRepresentation, REQUEST_DATE);
  }

  public DateTime getHoldShelfExpirationDate() {
    return getDateTimeProperty(requestRepresentation, HOLD_SHELF_EXPIRATION_DATE);
  }

  public DateTime getRequestExpirationDate() {
    return getDateTimeProperty(requestRepresentation, REQUEST_EXPIRATION_DATE);
  }

  public String getCancellationAdditionalInformation() {
    return getProperty(requestRepresentation, CANCELLATION_ADDITIONAL_INFORMATION);
  }

  public String getCancellationReasonId() {
    return getProperty(requestRepresentation, CANCELLATION_REASON_ID);
  }

  public String getCancellationReasonName() {
    return getProperty(cancellationReasonRepresentation, CANCELLATION_REASON_NAME);
  }

  public String getCancellationReasonPublicDescription() {
    return getProperty(cancellationReasonRepresentation, CANCELLATION_REASON_PUBLIC_DESCRIPTION);
  }
}
