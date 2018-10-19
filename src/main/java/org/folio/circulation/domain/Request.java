package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import java.lang.invoke.MethodHandles;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.RequestProperties;

import java.util.Objects;

import static org.folio.circulation.domain.RequestFulfilmentPreference.*;
import static org.folio.circulation.domain.RequestStatus.*;
import static org.folio.circulation.domain.representations.RequestProperties.STATUS;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Request implements ItemRelatedRecord, UserRelatedRecord, FindByIdQuery {
  private final JsonObject representation;
  private final Item item;
  private final User requester;
  private final User proxy;
  private final Loan loan;
  private boolean changedPosition = false;
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


  public Request(
    JsonObject representation,
    Item item,
    User requester,
    User proxy,
    Loan loan) {

    this.representation = representation;
    this.item = item;
    this.requester = requester;
    this.proxy = proxy;
    this.loan = loan;
  }

  public static Request from(JsonObject representation) {
    return new Request(representation, null, null, null, null);
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
    return new Request(representation, newItem, requester, proxy, loan);
  }

  public Request withRequester(User newRequester) {
    return new Request(representation, item, newRequester, proxy, loan);
  }

  public Request withProxy(User newProxy) {
    log.info(String.format("Request %s added proxy %s", representation.getString("id"), newProxy.getId()));
    return new Request(representation, item, requester, newProxy, loan);
  }
  
  public Request withLoan(Loan newLoan) {
    log.info(String.format("Request %s added loan %s", representation.getString("id"), newLoan.getId()));
    return new Request(representation, item, requester, proxy, newLoan);
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

  @Override
  public boolean userMatches(User user) {
    return user.getId().equals(requester.getId());
  }

  @Override
  public ValidationErrorFailure userDoesNotMatchError() {
    ValidationError error = new ValidationError("User does not match", "userId", requester.getId());
    return new ValidationErrorFailure(error);
  }

  public String getDeliveryAddressType() {
    return representation.getString("deliveryAddressTypeId");
  }
}
