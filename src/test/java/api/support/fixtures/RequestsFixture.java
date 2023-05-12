package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.MultipleJsonRecords.multipleRecordsFrom;
import static api.support.http.CqlQuery.noQuery;
import static api.support.http.InterfaceUrls.requestQueueUrl;
import static api.support.http.InterfaceUrls.requestsUrl;
import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.function.Function.identity;

import java.net.URL;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.Response;

import api.support.MultipleJsonRecords;
import api.support.RestAssuredClient;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.CqlQuery;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.Limit;
import api.support.http.Offset;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class RequestsFixture {
  private final String REQUESTS_COLLECTION_PROPERTY_NAME = "requests";

  private final ResourceClient requestsClient;
  private final CancellationReasonsFixture cancellationReasonsFixture;
  private final ServicePointsFixture servicePointsFixture;
  private final RestAssuredClient restAssuredClient;

  public RequestsFixture(ResourceClient requestsClient,
      CancellationReasonsFixture cancellationReasonsFixture,
      ServicePointsFixture servicePointsFixture) {

    this.requestsClient = requestsClient;
    this.cancellationReasonsFixture = cancellationReasonsFixture;
    this.servicePointsFixture = servicePointsFixture;
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public IndividualResource place(RequestBuilder requestToBuild) {
    return requestsClient.create(requestToBuild);
  }

  public Response attemptPlace(RequestBuilder requestToBuild) {
    return requestsClient.attemptCreate(requestToBuild);
  }

  public IndividualResource placeItemLevelHoldShelfRequest(IndividualResource item,
    IndividualResource by, ZonedDateTime on) {

    return place(new RequestBuilder()
      .hold()
      .fulfillToHoldShelf()
      .withItemId(item.getId())
      .withInstanceId(((ItemResource) item).getInstanceId())
      .withRequestDate(on)
      .withRequesterId(by.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
  }

  public IndividualResource placeItemLevelPageRequest(IndividualResource item, UUID instanceId,
    IndividualResource by) {

    return place(new RequestBuilder()
      .page()
      .fulfillToHoldShelf()
      .withItemId(item.getId())
      .withInstanceId(instanceId)
      .withRequestDate(ZonedDateTime.now())
      .withRequesterId(by.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
  }

  public IndividualResource placeItemLevelHoldShelfRequest(IndividualResource item,
    IndividualResource by) {

    return placeItemLevelHoldShelfRequest(item, by, ZonedDateTime.now());
  }

  public IndividualResource placeTitleLevelPageRequest(UUID instanceId, IndividualResource by) {
    return placeTitleLevelPageRequest(instanceId, by, servicePointsFixture.cd1().getId());
  }

  public IndividualResource placeTitleLevelPageRequest(UUID instanceId, IndividualResource by,
    UUID servicePointId) {

    return place(new RequestBuilder()
      .page()
      .fulfillToHoldShelf()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withRequestDate(ZonedDateTime.now())
      .withRequesterId(by.getId())
      .withPickupServicePointId(servicePointId));
  }

  public IndividualResource placeTitleLevelRecallRequest(UUID instanceId, IndividualResource by) {
    return place(new RequestBuilder()
      .recall()
      .fulfillToHoldShelf()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withRequestDate(ZonedDateTime.now())
      .withRequesterId(by.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
  }

  public IndividualResource placeTitleLevelHoldShelfRequest(UUID instanceId,
    IndividualResource by, ZonedDateTime on) {

    return place(new RequestBuilder()
      .hold()
      .fulfillToHoldShelf()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withRequestDate(on)
      .withRequesterId(by.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
  }

  public IndividualResource placeTitleLevelHoldShelfRequest(UUID instanceId,
    IndividualResource by) {

    return placeTitleLevelHoldShelfRequest(instanceId, by, ZonedDateTime.now());
  }

  public IndividualResource placeItemLevelDeliveryRequest(IndividualResource item,
      IndividualResource by, ZonedDateTime on) {

    return place(new RequestBuilder()
      .hold()
      .deliverToAddress(UUID.randomUUID())
      .withRequestDate(on)
      .withItemId(item.getId())
      .withInstanceId(((ItemResource) item).getInstanceId())
      .withRequesterId(by.getId()));
  }

  public IndividualResource placeItemLevelHoldShelfRequest(IndividualResource item, IndividualResource by,
    ZonedDateTime on, UUID pickupServicePointId) {

    return place(new RequestBuilder()
      .hold()
      .fulfillToHoldShelf()
      .withItemId(item.getId())
      .withInstanceId(((ItemResource) item).getInstanceId())
      .withRequestDate(on)
      .withRequesterId(by.getId())
      .withPickupServicePointId(pickupServicePointId));
  }

  public IndividualResource placeTitleLevelHoldShelfRequest(UUID instanceId,
    IndividualResource by, ZonedDateTime on, UUID pickupServicePointId) {

    return place(new RequestBuilder()
      .hold()
      .fulfillToHoldShelf()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withRequestDate(on)
      .withRequesterId(by.getId())
      .withPickupServicePointId(pickupServicePointId));
  }

  public IndividualResource placeTitleLevelRequest(RequestType requestType, UUID instanceId,
    IndividualResource requester) {

    return placeTitleLevelRequest(requestType, instanceId, requester, null);
  }

  public IndividualResource placeTitleLevelRequest(RequestType requestType, UUID instanceId,
    IndividualResource requester, LocalDate expirationDate) {

    return place(new RequestBuilder()
      .withRequestType(requestType.getValue())
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withRequesterId(requester.getId())
      .withRequestExpiration(expirationDate)
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
  }

  public IndividualResource placeItemLevelHoldShelfRequest(IndividualResource item,
      IndividualResource by, ZonedDateTime on, UUID pickupServicePointId, String type) {

    return place(new RequestBuilder()
      .hold()
      .withRequestType(type)
      .fulfillToHoldShelf()
      .withItemId(item.getId())
      .withInstanceId(((ItemResource) item).getInstanceId())
      .withRequestDate(on)
      .withRequesterId(by.getId())
      .withPickupServicePointId(pickupServicePointId));
  }

  public IndividualResource placeItemLevelHoldShelfRequest(IndividualResource item,
      IndividualResource by, ZonedDateTime on, String type) {

    return place(new RequestBuilder()
      .withRequestType(type)
      .deliverToAddress(UUID.randomUUID())
      .withRequestDate(on)
      .withItemId(item.getId())
      .withInstanceId(((ItemResource) item).getInstanceId())
      .withRequesterId(by.getId()));
  }

  public Response attemptPlaceItemLevelHoldShelfRequest(IndividualResource item,
    IndividualResource by, ZonedDateTime on, UUID pickupServicePointId, String type) {

    return attemptPlace(new RequestBuilder()
      .hold()
      .withRequestType(type)
      .fulfillToHoldShelf()
      .withItemId(item.getId())
      .withInstanceId(((ItemResource) item).getInstanceId())
      .withRequestDate(on)
      .withRequesterId(by.getId())
      .withPickupServicePointId(pickupServicePointId));
  }

  public Response attemptPlaceTitleLevelHoldShelfRequest(UUID instanceId,
    IndividualResource requester) {

    return attemptPlace(new RequestBuilder()
      .hold()
      .fulfillToHoldShelf()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withRequesterId(requester.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
  }

  public Response attemptPlaceHoldOrRecallTLR(UUID instanceId,
    IndividualResource requester, RequestType requestType) {

    if (requestType != RequestType.HOLD && requestType != RequestType.RECALL) {
      throw new IllegalArgumentException("Only Hold and Recall request are allowed." +
        " Provided request type: " + requestType);
    }
    RequestBuilder builder = new RequestBuilder()
      .fulfillToHoldShelf()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withRequesterId(requester.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId());

    return attemptPlace(RequestType.HOLD == requestType ? builder.hold() : builder.recall());
  }

  public Response attemptToPlaceForInstance(JsonObject representation) {
    return restAssuredClient.post(representation, requestsUrl("/instances"),
      "attempt-to-create-instance-request");
  }

  public void replaceRequest(UUID id, RequestBuilder updatedRequest) {
    restAssuredClient.put(updatedRequest.create(),
      individualRequestUrl(id), HTTP_NO_CONTENT, "replace-request");
  }

  public Response attemptToReplaceRequest(UUID id, RequestBuilder updatedRequest) {
    return restAssuredClient.put(updatedRequest.create(),
      individualRequestUrl(id), "attempt-to-replace-request");
  }

  public void cancelRequest(IndividualResource request) {
    final RequestBuilder cancelledRequestBySteve = buildCancelledRequest(request);
    requestsClient.replace(request.getId(), cancelledRequestBySteve);
  }

  public Response attemptCancelRequest(IndividualResource request) {
    final RequestBuilder cancelledRequestBySteve = buildCancelledRequest(request);

    return requestsClient.attemptReplace(request.getId(), cancelledRequestBySteve);
  }

  private RequestBuilder buildCancelledRequest(IndividualResource request) {

    final IndividualResource courseReservesCancellationReason
      = cancellationReasonsFixture.courseReserves();

    return RequestBuilder.from(request)
      .cancelled()
      .withCancellationReasonId(courseReservesCancellationReason.getId());
  }

  public void expireRequest(IndividualResource request) {
    requestsClient.replace(request.getId(),
      RequestBuilder.from(request)
        .withPosition(null)
        .withStatus(RequestBuilder.CLOSED_UNFILLED));
  }

  public void expireRequestPickup(IndividualResource request) {
    requestsClient.replace(request.getId(),
      RequestBuilder.from(request)
        .withPosition(null)
        .withStatus(RequestBuilder.CLOSED_PICKUP_EXPIRED));
  }

  public IndividualResource move(MoveRequestBuilder requestToBuild) {
    final JsonObject representation = requestToBuild.create();

    return new IndividualResource(restAssuredClient.post(representation,
        requestsUrl(pathToMoveRequest(representation)), HTTP_OK, "move-request"));
  }

  public Response attemptMove(MoveRequestBuilder requestToBuild) {
    final JsonObject representation = requestToBuild.create();

    return restAssuredClient.post(representation,
      requestsUrl(pathToMoveRequest(representation)), "move-request");
  }

  public Response getById(UUID id) {
    return requestsClient.getById(id);
  }

  public MultipleJsonRecords getAllRequests() {
    return getRequests(noQuery(), noLimit(), noOffset());
  }

  public MultipleJsonRecords getRequests(CqlQuery query, Limit limit, Offset offset) {
    return multipleRecordsFrom(restAssuredClient.get(requestsUrl(), query,
      limit, offset, HTTP_OK, "get-requests"), REQUESTS_COLLECTION_PROPERTY_NAME);
  }

  //TODO: Replace return type with MultipleJsonRecords
  public MultipleRecords<JsonObject> getQueueFor(IndividualResource item) {
    return MultipleRecords.from(restAssuredClient.get(
        requestQueueUrl(item.getId()), HTTP_OK, "request-queue-request"),
        identity(), REQUESTS_COLLECTION_PROPERTY_NAME).value();
  }

  public Response deleteAllRequests() {
    return restAssuredClient.delete(requestsUrl(), HTTP_NO_CONTENT,
      "delete-all-requests");
  }

  public void deleteRequest(UUID requestId) {
    restAssuredClient.delete(individualRequestUrl(requestId),
      HTTP_NO_CONTENT, "delete-a-request");
  }

  private URL individualRequestUrl(UUID requestId) {
    return requestsUrl(String.format("/%s", requestId));
  }

  private String pathToMoveRequest(JsonObject representation) {
    return String.format("/%s/move", representation.getString("id"));
  }

  public void recallItem(IndividualResource item, IndividualResource user) {
    place(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(user)
      .fulfillToHoldShelf()
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
  }
}
