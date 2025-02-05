package api.support.builders;

import static api.support.utl.DateTimeUtils.getLocalDatePropertyForDateWithTime;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_LOCATION_CODE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getLocalDateProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.override.BlockOverrides;

import api.support.http.IndividualResource;
import api.support.http.ItemResource;
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
  public static final String CLOSED_UNFILLED = "Closed - Unfilled";
  public static final String CLOSED_CANCELLED = "Closed - Cancelled";
  public static final String CLOSED_PICKUP_EXPIRED = "Closed - Pickup expired";

  private final UUID id;
  private final String requestType;
  private final String requestLevel;
  private final ZonedDateTime requestDate;
  private final UUID itemId;
  private final UUID holdingsRecordId;
  private final UUID instanceId;
  private final UUID requesterId;
  private final String fulfillmentPreference;
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
  private final ZonedDateTime cancelledDate;
  private final Integer position;
  private final UUID pickupServicePointId;
  private final Tags tags;
  private final String patronComments;
  private final BlockOverrides blockOverrides;
  private final String ecsRequestPhase;
  private final String itemLocationCode;
  private final PrintDetails printDetails;

  public RequestBuilder() {
    this(UUID.randomUUID(),
      "Hold",
      "Item",
      ZonedDateTime.of(2017, 7, 15, 9, 35, 27, 0, UTC),
      UUID.randomUUID(),
      UUID.randomUUID(),
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
      getProperty(representation, "requestLevel"),
      getDateTimeProperty(representation, "requestDate"),
      getUUIDProperty(representation, "itemId"),
      getUUIDProperty(representation, "holdingsRecordId"),
      getUUIDProperty(representation, "instanceId"),
      getUUIDProperty(representation, "requesterId"),
      getProperty(representation, "fulfillmentPreference"),
      getUUIDProperty(representation, "deliveryAddressTypeId"),
      getLocalDatePropertyForDateWithTime(representation, "requestExpirationDate"),
      getLocalDateProperty(representation, "holdShelfExpirationDate"),
      ItemSummary.fromRepresentation(representation),
      null, //TODO, re-populate these from the representation (possibly shouldn't given use)
      getProperty(representation, "status"),
      getUUIDProperty(representation, "proxyUserId"),
      getUUIDProperty(representation, "cancellationReasonId"),
      getUUIDProperty(representation, "cancelledByUserId"),
      getProperty(representation, "cancellationAdditionalInformation"),
      getDateTimeProperty(representation, "cancelledDate"),
      getIntegerProperty(representation, "position", null),
      getUUIDProperty(representation, "pickupServicePointId"),
      new Tags((toStream(representation.getJsonObject("tags"), "tagList").collect(toList()))),
      getProperty(representation, "patronComments"),
      null,
      getProperty(representation, "ecsRequestPhase"),
      getProperty(representation, ITEM_LOCATION_CODE),
      PrintDetails.fromRepresentation(representation)
    );
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "id", this.id);
    put(request, "requestType", this.requestType);
    put(request, "requestLevel", this.requestLevel);
    put(request, "requestDate", formatDateTimeOptional(this.requestDate));
    put(request, "itemId", this.itemId);
    put(request, "holdingsRecordId", this.holdingsRecordId);
    put(request, "instanceId", this.instanceId);
    put(request, "requesterId", this.requesterId);
    put(request, "fulfillmentPreference", this.fulfillmentPreference);
    put(request, "position", this.position);
    put(request, "status", this.status);
    put(request, "deliveryAddressTypeId", this.deliveryAddressType);
    put(request, "requestExpirationDate", this.requestExpiration);
    put(request, "holdShelfExpirationDate", this.holdShelfExpiration);
    put(request, "proxyUserId", userProxyId);
    put(request, "cancellationReasonId", cancellationReasonId);
    put(request, "cancelledByUserId", cancelledByUserId);
    put(request, "cancellationAdditionalInformation", cancellationAdditionalInformation);
    put(request, "cancelledDate", formatDateTimeOptional(cancelledDate));
    put(request, "pickupServicePointId", this.pickupServicePointId);
    put(request, "patronComments", this.patronComments);
    put(request, ITEM_LOCATION_CODE, this.itemLocationCode);

    if (itemSummary != null) {
      final JsonObject itemRepresentation = new JsonObject();

      put(itemRepresentation, "barcode", itemSummary.barcode);

      put(itemRepresentation, "itemEffectiveLocationId", itemSummary.itemEffectiveLocationId);
      put(itemRepresentation, "itemEffectiveLocationName", itemSummary.itemEffectiveLocationName);
      put(itemRepresentation, "retrievalServicePointId", itemSummary.retrievalServicePointId);
      put(itemRepresentation, "retrievalServicePointName", itemSummary.retrievalServicePointName);

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

    if (blockOverrides != null) {
      JsonObject overrideBlocks = new JsonObject();

      if (blockOverrides.getPatronBlockOverride() != null &&
        blockOverrides.getPatronBlockOverride().isRequested()) {

        overrideBlocks.put("patronBlock", new JsonObject());
      }

      if (!overrideBlocks.isEmpty()) {
        JsonObject processingParameters = new JsonObject().put("overrideBlocks", overrideBlocks);
        put(request, "requestProcessingParameters", processingParameters);
      }
    }

    if (ecsRequestPhase != null) {
      put(request, "ecsRequestPhase", ecsRequestPhase);
    }

    if (printDetails != null) {
      put(request, "printDetails", printDetails.toJsonObject());
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

  public RequestBuilder itemRequestLevel() {
    return withRequestLevel("Item");
  }

  public RequestBuilder titleRequestLevel() {
    return withRequestLevel("Title");
  }

  public RequestBuilder withNoInstanceId() {
    return withInstanceId(null);
  }

  public RequestBuilder withNoItemId() {
    return withItemId(null);
  }

  public RequestBuilder withNoHoldingsRecordId() {
    return withHoldingsRecordId(null);
  }

  public RequestBuilder forItem(IndividualResource item) {
    RequestBuilder builder = withItemId(item.getId());

    if (item instanceof ItemResource) {
      ItemResource itemResource = (ItemResource) item;
      return builder.withInstanceId(itemResource.getInstanceId())
        .withHoldingsRecordId(itemResource.getHoldingsRecordId());
    }

    return builder;
  }

  public RequestBuilder by(IndividualResource requester) {
    return withRequesterId(requester.getId());
  }

  public RequestBuilder deliverToAddress(UUID addressTypeId) {
    return withFulfillmentPreference("Delivery")
      .withDeliveryAddressType(addressTypeId);
  }

  //TODO: Remove, and combine with service point to be fulfilled to
  public RequestBuilder fulfillToHoldShelf() {
    return withFulfillmentPreference("Hold Shelf");
  }

  public RequestBuilder fulfillToHoldShelf(IndividualResource newPickupServicePoint) {
    return withFulfillmentPreference("Hold Shelf")
      .withPickupServicePointId(newPickupServicePoint.getId());
  }

  public RequestBuilder fulfillToHoldShelf(UUID newPickupServicePointId) {
    return withFulfillmentPreference("Hold Shelf")
      .withPickupServicePointId(newPickupServicePointId);
  }

  public RequestBuilder withNoRequestExpiration() {
    return withRequestExpiration(null);
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
  public static class ItemSummary {
    private final String barcode;
    private final String itemEffectiveLocationId;
    private final String itemEffectiveLocationName;
    private final String retrievalServicePointId;
    private final String retrievalServicePointName;

    public static ItemSummary fromRepresentation(JsonObject representation) {
      JsonObject item = representation.getJsonObject("item");
      String barcode = null;
      String itemEffectiveLocationId = null;
      String itemEffectiveLocationName = null;
      String retrievalServicePointId = null;
      String retrievalServicePointName = null;
      if (item != null) {
        barcode = item.getString("barcode");
        itemEffectiveLocationId = item.getString("itemEffectiveLocationId");
        itemEffectiveLocationName = item.getString("itemEffectiveLocationName");
        retrievalServicePointId = item.getString("retrievalServicePointId");
        retrievalServicePointName = item.getString("retrievalServicePointName");
      }
      return new ItemSummary(barcode, itemEffectiveLocationId,
        itemEffectiveLocationName, retrievalServicePointId,
        retrievalServicePointName);
    }
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

  @AllArgsConstructor
  @Getter
  public static class PrintDetails {
    private final Integer printCount;
    private final String requesterId;
    private final Boolean isPrinted;
    private final String printEventDate;

    public static PrintDetails fromRepresentation(JsonObject representation) {
      JsonObject printDetails = representation.getJsonObject("printDetails");
      if (printDetails != null) {
        final Integer printCount = printDetails.getInteger("printCount");
        final String requesterId = printDetails.getString("requesterId");
        final Boolean isPrinted = printDetails.getBoolean("isPrinted");
        final String printEventDate = printDetails.getString("printEventDate");
        return new PrintDetails(printCount, requesterId, isPrinted,
          printEventDate);
      }
      return null;
    }

    public JsonObject toJsonObject() {
      JsonObject printDetails = new JsonObject();
      printDetails.put("printCount", printCount);
      printDetails.put("requesterId", requesterId);
      printDetails.put("isPrinted", isPrinted);
      printDetails.put("printEventDate", printEventDate);
      return  printDetails;
    }
  }
}
