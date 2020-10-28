package api.support.builders;

import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getLocalDateProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.Request;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@With
@AllArgsConstructor
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
  private final UUID deliveryAddressType;
  private final LocalDate requestExpiration;
  private final LocalDate holdShelfExpiration;
  private final ItemSummary itemSummary;
  private final PatronSummary requesterSummary;
  private final String status;
  private final UUID userProxyId;
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
    put(request, "deliveryAddressTypeId", this.deliveryAddressType);
    put(request, "requestExpirationDate", this.requestExpiration);
    put(request, "holdShelfExpirationDate", this.holdShelfExpiration);
    put(request, "proxyUserId", userProxyId);
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

  public Request asDomainObject() {
    return Request.from(create());
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

  public RequestBuilder withNoItemId() {
    return withItemId(null);
  }

  public RequestBuilder forItem(IndividualResource item) {
    return withItemId(item.getId());
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

  public RequestBuilder withRequestExpirationJavaDate(java.time.LocalDate requestExpiration) {
    return withRequestExpiration(new LocalDate(requestExpiration.getYear(),
      requestExpiration.getMonthValue(), requestExpiration.getDayOfMonth()));
  }

  public RequestBuilder withNoRequestExpiration() {
    return withRequestExpiration(null);
  }

  public RequestBuilder withHoldShelfExpirationJavaDate(java.time.LocalDate holdShelfExpiration) {
    return withHoldShelfExpiration(new LocalDate(holdShelfExpiration.getYear(),
      holdShelfExpiration.getMonthValue(), holdShelfExpiration.getDayOfMonth()));
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

  public RequestBuilder proxiedBy(IndividualResource proxy) {
    return withUserProxyId(proxy.getId());
  }

  public RequestBuilder withPickupServicePoint(IndividualResource newPickupServicePoint) {
    return withPickupServicePointId(newPickupServicePoint.getId());
  }

  @AllArgsConstructor
  private static class ItemSummary {
    private final String title;
    private final String barcode;
  }

  @AllArgsConstructor
  private static class PatronSummary {
    private final String lastName;
    private final String firstName;
    private final String middleName;
    private final String barcode;
  }

  @Getter
  @AllArgsConstructor
  public static class Tags {
    private final List<String> tagList;
  }
}
