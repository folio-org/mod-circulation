package org.folio.circulation.domain;

import static org.folio.circulation.domain.RequestFulfilmentPreference.HOLD_SHELF;
import static org.folio.circulation.domain.RequestStatus.CLOSED_CANCELLED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_FILLED;
import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.domain.RequestStatus.OPEN_NOT_YET_FILLED;
import static org.folio.circulation.domain.representations.RequestProperties.STATUS;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.RequestProperties;

import io.vertx.core.json.JsonObject;

public class Request implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;
  private final Item item;
  private final User requester;
  private final User proxy;
  private final Loan loan;
  private final ServicePoint pickupServicePoint;
  
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

  boolean isOpen() {
    RequestStatus status = getStatus();

    return status == OPEN_AWAITING_PICKUP
      || status == OPEN_NOT_YET_FILLED;
  }

  boolean isCancelled() {
    return getStatus() == CLOSED_CANCELLED;
  }

  private boolean isFulfilled() {
    return getStatus() == CLOSED_FILLED;
  }

  public boolean isClosed() {
    //Alternatively, check status contains "Closed"
    return isCancelled() || isFulfilled();
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
  
  Request withLoan(Loan newLoan) {
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

  private RequestFulfilmentPreference getFulfilmentPreference() {
    return RequestFulfilmentPreference.from(getFulfilmentPreferenceName());
  }

  public String getId() {
    return representation.getString("id");
  }

  private RequestType getRequestType() {
    return RequestType.from(representation.getString("requestType"));
  }

  Boolean allowedForItem() {
    return getRequestType().canCreateRequestForItem(getItem());
  }

  String actionOnCreation() {
    return getRequestType().toLoanAction();
  }

  RequestStatus getStatus() {
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
    if(!Objects.equals(getPosition(), newPosition)) {
      write(representation, RequestProperties.POSITION, newPosition);
      changedPosition = true;
    }

    return this;
  }

  void removePosition() {
    representation.remove(RequestProperties.POSITION);
    changedPosition = true;
  }

  public Integer getPosition() {
    return getIntegerProperty(representation, RequestProperties.POSITION, null);
  }

  boolean hasChangedPosition() {
    return changedPosition;
  }

  ItemStatus checkedInItemStatus() {
    return getFulfilmentPreference().toCheckedInItemStatus();
  }

  ItemStatus checkedOutItemStatus() {
    return getRequestType().toCheckedOutItemStatus();
  }

  String getDeliveryAddressType() {
    return representation.getString("deliveryAddressTypeId");
  }
}
