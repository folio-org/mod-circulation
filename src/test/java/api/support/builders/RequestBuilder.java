package api.support.builders;

import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getLocalDateProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.JsonStringArrayHelper.toStream;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import io.vertx.core.json.JsonObject;

public class RequestBuilder extends JsonBuilder implements Builder {
  public static final String OPEN_NOT_YET_FILLED = "Open - Not yet filled";
  public static final String OPEN_AWAITING_PICKUP = "Open - Awaiting pickup";
  public static final String OPEN_AWAITING_DELIVERY = "Open - Awaiting delivery";
  public static final String OPEN_IN_TRANSIT = "Open - In transit";
  public static final String CLOSED_FILLED = "Closed - Filled";
  public static final String CLOSED_CANCELLED = "Closed - Cancelled";

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
  private final UUID cancellationReasonId;
  private final UUID cancelledByUserId;
  private final String cancellationAdditionalInformation;
  private final DateTime cancelledDate;
  private final Integer position;
  private final UUID pickupServicePointId;
  private final Tags tags;

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
      null,
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
    UUID proxyUserId,
    UUID cancellationReasonId,
    UUID cancelledByUserId,
    String cancellationAdditionalInformation,
    DateTime cancelledDate,
    Integer position,
    UUID pickupServicePointId,
    Tags tags) {

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
    this.cancellationReasonId = cancellationReasonId;
    this.cancelledByUserId = cancelledByUserId;
    this.cancellationAdditionalInformation = cancellationAdditionalInformation;
    this.cancelledDate = cancelledDate;
    this.position = position;
    this.pickupServicePointId = pickupServicePointId;
    this.tags = tags;
  }

  public static RequestBuilder from(IndividualResource response) {
    JsonObject representation = response.getJson();

    return new RequestBuilder(
      UUID.fromString(representation.getString("id")),
      getProperty(representation, "requestType"),
      getDateTimeProperty(representation, "requestDate"),
      getUUIDProperty(representation, "itemId"),
      getUUIDProperty(representation, "requesterId"),
      getProperty(representation, "fulfilmentPreference"),
      getUUIDProperty(representation, "deliveryAddressTypeId"),
      getLocalDateProperty(representation, "requestExpirationDate"),
      getLocalDateProperty(representation, "holdShelfExpirationDate"),
      null, //TODO, re-populate these from the representation (possibly shouldn't given use)
      null, //TODO, re-populate these from the representation (possibly shouldn't given use)
      getProperty(representation, "status"),
      getUUIDProperty(representation, "proxyUserId"),
      getUUIDProperty(representation, "cancellationReasonId"),
      getUUIDProperty(representation, "cancelledByUserId"),
      getProperty(representation, "cancellationAdditionalInformation"),
      getDateTimeProperty(representation, "cancelledDate"),
      getIntegerProperty(representation, "position", null),
      getUUIDProperty(representation, "pickupServicePointId"),
      new Tags((toStream(representation.getJsonObject("tags"), "tagList").collect(toList())))
    );
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "id", this.id);
    put(request, "requestType", this.requestType);
    put(request, "requestDate", this.requestDate);
    put(request, "itemId", this.itemId);
    put(request, "requesterId", this.requesterId);
    put(request, "fulfilmentPreference", this.fulfilmentPreference);
    put(request, "position", this.position);
    put(request, "status", this.status);
    put(request, "deliveryAddressTypeId", this.deliveryAddressTypeId);
    put(request, "requestExpirationDate", this.requestExpirationDate);
    put(request, "holdShelfExpirationDate", this.holdShelfExpirationDate);
    put(request, "proxyUserId", proxyUserId);
    put(request, "cancellationReasonId", cancellationReasonId);
    put(request, "cancelledByUserId", cancelledByUserId);
    put(request, "cancellationAdditionalInformation", cancellationAdditionalInformation);
    put(request, "cancelledDate", cancelledDate);
    put(request, "pickupServicePointId", this.pickupServicePointId);

    if (itemSummary != null) {
      final JsonObject itemRepresentation = new JsonObject();

      put(itemRepresentation, "title", itemSummary.title);
      put(itemRepresentation, "barcode", itemSummary.barcode);

      put(request, "item", itemRepresentation);
    }

    if (requesterSummary != null) {
      JsonObject requester = new JsonObject();

      put(requester, "lastName", requesterSummary.lastName);
      put(requester, "firstName", requesterSummary.firstName);
      put(requester, "middleName", requesterSummary.middleName);
      put(requester, "barcode", requesterSummary.barcode);

      put(request, "requester", requester);
    }

    if (tags != null) {
      JsonObject tags = new JsonObject();
      tags.put("tagList", this.tags.getTagList());

      put(request, "tags", tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestBuilder by(IndividualResource requester) {
    return withRequesterId(requester.getId());
  }

  public RequestBuilder deliverToAddress(UUID addressTypeId) {
    return withFulfilmentPreference("Delivery")
      .withDeliveryAddressType(addressTypeId);
  }

  //TODO: Remove, and combine with service point to be fulfilled to
  public RequestBuilder fulfilToHoldShelf() {
    return withFulfilmentPreference("Hold Shelf");
  }

  public RequestBuilder fulfilToHoldShelf(IndividualResource newPickupServicePoint) {
    return withFulfilmentPreference("Hold Shelf")
      .withPickupServicePointId(newPickupServicePoint.getId());
  }

  public RequestBuilder fulfilToHoldShelf(UUID newPickupServicePointId) {
    return withFulfilmentPreference("Hold Shelf")
      .withPickupServicePointId(newPickupServicePointId);
  }

  private RequestBuilder withFulfilmentPreference(String fulfilmentPreference) {
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestBuilder open() {
    return withStatus(OPEN_NOT_YET_FILLED);
  }

  public RequestBuilder withNoStatus() {
    return withStatus(null);
  }

  public RequestBuilder fulfilled() {
    return withStatus(CLOSED_FILLED);
  }

  public RequestBuilder cancelled() {
    return withStatus(CLOSED_CANCELLED);
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
      userProxyId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestBuilder withCancellationReasonId(UUID cancellationReasonId) {
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
      this.proxyUserId,
      cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestBuilder withCancelledByUserId(UUID cancelledByUserId) {
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
      this.proxyUserId,
      this.cancellationReasonId,
      cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestBuilder withCancellationAdditionalInformation(
    String cancellationAdditionalInformation) {

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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestBuilder withCancelledDate(DateTime cancelledDate) {
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      cancelledDate,
      this.position,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestBuilder proxiedBy(IndividualResource proxy) {
    return withUserProxyId(proxy.getId());
  }

  public RequestBuilder withPosition(Integer newPosition) {
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      newPosition,
      this.pickupServicePointId,
      this.tags);
  }

  public RequestBuilder withPickupServicePoint(IndividualResource newPickupServicePoint) {
    return withPickupServicePointId(newPickupServicePoint.getId());
  }

  public RequestBuilder withPickupServicePointId(UUID newPickupServicePointId) {
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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      newPickupServicePointId,
      this.tags);
  }


  public RequestBuilder withTags(Tags tags) {

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
      this.proxyUserId,
      this.cancellationReasonId,
      this.cancelledByUserId,
      this.cancellationAdditionalInformation,
      this.cancelledDate,
      this.position,
      this.pickupServicePointId,
      tags);
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


  public static class Tags {

    public final List<String> tagList;


    public Tags(List<String> tagList) {

      this.tagList = tagList;
    }


    public List<String> getTagList() {

      return tagList;
    }
  }
}
