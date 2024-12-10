package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.circulation.domain.RequestFulfillmentPreference.DELIVERY;
import static org.folio.circulation.domain.RequestFulfillmentPreference.HOLD_SHELF;
import static org.folio.circulation.domain.RequestLevel.ITEM;
import static org.folio.circulation.domain.RequestLevel.TITLE;
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
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_LOCATION_CODE;
import static org.folio.circulation.domain.representations.RequestProperties.ECS_REQUEST_PHASE;
import static org.folio.circulation.domain.representations.RequestProperties.HOLDINGS_RECORD_ID;
import static org.folio.circulation.domain.representations.RequestProperties.HOLD_SHELF_EXPIRATION_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.INSTANCE_ID;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_ID;
import static org.folio.circulation.domain.representations.RequestProperties.POSITION;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_EXPIRATION_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_LEVEL;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.domain.representations.RequestProperties.STATUS;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.RequestPolicy;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

@AllArgsConstructor
@Getter
@ToString(onlyExplicitlyIncluded = true)
public class Request implements ItemRelatedRecord, UserRelatedRecord {
  @With
  private final TlrSettingsConfiguration tlrSettingsConfiguration;

  @ToString.Include
  @With
  private final Operation operation;

  @ToString.Include
  @With
  private final JsonObject requestRepresentation;

  @With
  private final JsonObject cancellationReasonRepresentation;

  @With
  private final Instance instance;

  @With
  private final Collection<Item> instanceItems;

  /**
   * A map of request policies for each item of the instance: (String itemId, RequestPolicy policy)
   */
  @With
  private final Map<String, RequestPolicy> instanceItemsRequestPolicies;

  private final Item item;

  @With
  private final User requester;

  @With
  private final User proxy;

  @With
  private final AddressType addressType;

  // For TLR there can be multiple loans, only using the first one.
  @With
  private final Loan loan;

  @With
  private final ServicePoint pickupServicePoint;

  private boolean changedPosition;
  private Integer previousPosition;
  private boolean changedStatus;

  @With
  private final User printDetailsRequester;

  public static Request from(JsonObject representation) {
    // TODO: make sure that operation and TLR settings don't matter for all processes calling
    //  this constructor
    return new Request(null, null, representation, null, null, new ArrayList<>(), new HashMap<>(),
    null, null, null, null, null, null, false, null,
      false, null);
  }

  public static Request from(TlrSettingsConfiguration tlrSettingsConfiguration, Operation operation,
    JsonObject representation) {

    return new Request(tlrSettingsConfiguration, operation, representation, null, null,
      new ArrayList<>(), new HashMap<>(), null, null, null, null, null, null, false,
      null, false, null);
  }

  public JsonObject asJson() {
    return requestRepresentation.copy();
  }

  boolean isFulfillable() {
    return getfulfillmentPreference() == HOLD_SHELF || getfulfillmentPreference() == DELIVERY;
  }

  public boolean isPage() {
    return getRequestType() == RequestType.PAGE;
  }

  public boolean isHold() {
    return getRequestType() == RequestType.HOLD;
  }

  public boolean isOpen() {
    return isAwaitingPickup() || isNotYetFilled() || isInTransit();
  }

  private boolean isInTransit() {
    return getStatus() == OPEN_IN_TRANSIT;
  }

  public boolean isNotYetFilled() {
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

  public boolean isPickupExpired() {
    return getStatus() == CLOSED_PICKUP_EXPIRED;
  }

  public boolean isClosed() {
    return isCancelled() || isFulfilled() || isUnfilled() || isPickupExpired();
  }

  public boolean isAwaitingPickup() {
    return getStatus() == OPEN_AWAITING_PICKUP;
  }

  public boolean isFor(User user) {
    return StringUtils.equals(getUserId(), user.getId());
  }

  public boolean isFor(Item item) {
    return isFor(item.getItemId());
  }

  public boolean isFor(Loan loan) {
    return isFor(loan.getItemId());
  }

  public boolean isFor(String itemId) {
    return StringUtils.equals(getItemId(), itemId);
  }

  public String getInstanceId() {
    return requestRepresentation.getString(INSTANCE_ID);
  }

  public boolean isRecall() {
    return getRequestType() == RequestType.RECALL;
  }

  public boolean isTitleLevel() {
    return getRequestLevel() == TITLE;
  }

  public boolean isItemLevel() {
    return getRequestLevel() == ITEM;
  }

  @Override
  public String getItemId() {
    return requestRepresentation.getString(ITEM_ID);
  }

  public String getHoldingsRecordId() {
    return requestRepresentation.getString(HOLDINGS_RECORD_ID);
  }

  public Request withItem(Item newItem) {
    // NOTE: this is null in RequestsAPIUpdatingTests.replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist test
    if (newItem != null && newItem.getItemId() != null && newItem.getHoldingsRecordId() != null) {
      requestRepresentation.put(ITEM_ID, newItem.getItemId());
      requestRepresentation.put(HOLDINGS_RECORD_ID, newItem.getHoldingsRecordId());
    }

    return new Request(tlrSettingsConfiguration, operation, requestRepresentation,
      cancellationReasonRepresentation, instance, instanceItems, instanceItemsRequestPolicies,
      newItem, requester, proxy, addressType,
      loan == null ? null : loan.withItem(newItem), pickupServicePoint, changedPosition,
      previousPosition, changedStatus, null);
  }

  @Override
  public String getUserId() {
    return requestRepresentation.getString("requesterId");
  }

  @Override
  public String getProxyUserId() {
    return requestRepresentation.getString("proxyUserId");
  }

  @Override
  public User getUser() {
    return getRequester();
  }

  public User getPrintDetailsRequester() {
    return printDetailsRequester;
  }

  public String getfulfillmentPreferenceName() {
    return requestRepresentation.getString("fulfillmentPreference");
  }

  public RequestFulfillmentPreference getfulfillmentPreference() {
    return RequestFulfillmentPreference.from(getfulfillmentPreferenceName());
  }

  public String getId() {
    return requestRepresentation.getString("id");
  }

  public RequestLevel getRequestLevel() {
    return RequestLevel.from(getProperty(requestRepresentation, REQUEST_LEVEL));
  }

  public RequestType getRequestType() {
    return RequestType.from(getProperty(requestRepresentation, REQUEST_TYPE));
  }

  public EcsRequestPhase getEcsRequestPhase() {
    return EcsRequestPhase.from(getProperty(requestRepresentation, ECS_REQUEST_PHASE));
  }

  boolean allowedForItem() {
    return RequestTypeItemStatusWhiteList.canCreateRequestForItem(getItem().getStatus(), getRequestType());
  }

  LoanAction actionOnCreateOrUpdate() {
    return getRequestType().toLoanAction();
  }

  public RequestStatus getStatus() {
    return RequestStatus.from(requestRepresentation.getString(STATUS));
  }

  void changeStatus(RequestStatus newStatus) {
    if (getStatus() != newStatus) {
      newStatus.writeTo(requestRepresentation);
      changedStatus = true;
    }
  }

  Request withRequestType(RequestType type) {
    requestRepresentation.put(REQUEST_TYPE, type.getValue());
    return this;
  }

  public JsonObject getRequesterFromRepresentation() {
    return requestRepresentation.getJsonObject("requester");
  }

  public JsonObject getPrintDetails() {
    return requestRepresentation.getJsonObject("printDetails");
  }

  public String getRequesterBarcode() {
    return getRequesterFromRepresentation().getString("barcode", EMPTY);
  }

  public String getRequesterId() {
    return requestRepresentation.getString("requesterId", EMPTY);
  }

  public String getPickupServicePointId() {
    return requestRepresentation.getString("pickupServicePointId");
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

  public boolean hasChangedPosition() {
    return changedPosition;
  }

  ItemStatus checkedInItemStatus() {
    return getfulfillmentPreference().toCheckedInItemStatus();
  }

  public String getDeliveryAddressTypeId() {
    return requestRepresentation.getString("deliveryAddressTypeId");
  }

  void changeHoldShelfExpirationDate(ZonedDateTime holdShelfExpirationDate) {
    write(requestRepresentation, HOLD_SHELF_EXPIRATION_DATE,
      holdShelfExpirationDate);
  }

  void removeHoldShelfExpirationDate() {
    requestRepresentation.remove(HOLD_SHELF_EXPIRATION_DATE);
  }

  public ZonedDateTime getRequestDate() {
    return getDateTimeProperty(requestRepresentation, REQUEST_DATE);
  }

  public ZonedDateTime getHoldShelfExpirationDate() {
    return getDateTimeProperty(requestRepresentation, HOLD_SHELF_EXPIRATION_DATE);
  }

  public ZonedDateTime getRequestExpirationDate() {
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

  public Request copy() {
    return withRequestRepresentation(requestRepresentation.copy());
  }

  public String getPatronComments() {
    return getProperty(requestRepresentation, "patronComments");
  }

  public String geItemLocationCode() {
    return getProperty(requestRepresentation, ITEM_LOCATION_CODE);
  }

  public Request truncateRequestExpirationDateToTheEndOfTheDay(ZoneId zone) {
    ZonedDateTime requestExpirationDate = getRequestExpirationDate();
    if (requestExpirationDate != null) {
      final ZonedDateTime dateTime = atEndOfDay(requestExpirationDate, zone);
      write(requestRepresentation, REQUEST_EXPIRATION_DATE, dateTime);
    }
    return this;
  }

  public boolean hasTopPriority() {
    return Integer.valueOf(1).equals(getPosition());
  }

  public boolean hasChangedStatus() {
    return changedStatus;
  }

  public boolean hasItemId() {
    return isNotBlank(getItemId());
  }

  public boolean hasItem() {
    return item != null && item.isFound();
  }

  public boolean hasLoan() {
    return loan != null;
  }

  public boolean getDcbReRequestCancellationValue() {
    return getBooleanProperty(requestRepresentation, "isDcbReRequestCancellation");
  }

  public enum Operation {
    CREATE, REPLACE, MOVE;
  }
}
