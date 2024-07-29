package api.printEvents;

import api.support.APITests;
import api.support.builders.CirculationSettingBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.InterfaceUrls.printEventsUrl;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class PrintEventsTests extends APITests {

  private IndividualResource servicePointResource;
  private UserResource userResource;

  @BeforeEach
  void executeBefore() {
     userResource = usersFixture.charlotte();
     servicePointResource = servicePointsFixture.cd1();
     circulationSettingsClient.deleteAll();
  }

  @Test
  void postPrintEventsTest() {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", true)));
    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", createMultipleRequests(100, "test-"));
    System.out.println(printRequest.getString("requestIds"));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_CREATED));
  }

  @Test
  void postPrintEventsAndFetchPrintDetailsInRequestApi() {
    var itemBarcodePrefix = "itemBarcode-";

    assertThat("Circulation settings enabled", circulationSettingsClient.getAll().isEmpty());

    // creating 2 different requests and assert request details without enabling printEvent feature
    var uuidList = createMultipleRequests(2, itemBarcodePrefix);
    JsonObject requestRepresentation1 = requestsClient.getMany(exactMatch("id", uuidList.get(0))).getFirst();
    JsonObject requestRepresentation2 = requestsClient.getMany(exactMatch("id", uuidList.get(1))).getFirst();
    assertRequestDetails(requestRepresentation1, uuidList.get(0), itemBarcodePrefix+0);
    assertRequestDetails(requestRepresentation2, uuidList.get(1), itemBarcodePrefix+1);
    assertThat("printDetails should be null for request1 because the print event feature is not enabled",
      requestRepresentation1.getJsonObject("printDetails"), Matchers.nullValue());
    assertThat("printDetails should be null for request2 because the print event feature is not enabled",
      requestRepresentation2.getJsonObject("printDetails"), Matchers.nullValue());

    // Enabling printEvent feature and assert request details.
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", true)));
    requestRepresentation1 = requestsClient.getMany(exactMatch("id", uuidList.get(0))).getFirst();
    requestRepresentation2 = requestsClient.getMany(exactMatch("id", uuidList.get(1))).getFirst();
    assertRequestDetails(requestRepresentation1, uuidList.get(0), itemBarcodePrefix+0);
    assertRequestDetails(requestRepresentation2, uuidList.get(1), itemBarcodePrefix+1);
    assertThat("printDetails should be null for request1 because the request is not printed",
      requestRepresentation1.getJsonObject("printDetails"), Matchers.nullValue());
    assertThat("printDetails should be null for request2 because the request is not printed",
      requestRepresentation2.getJsonObject("printDetails"), Matchers.nullValue());

    // Printing both request1 and request2
    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", uuidList);
    printRequest.put("requesterId", userResource.getId());
    restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    requestRepresentation1 = requestsClient.getMany(exactMatch("id", uuidList.get(0))).getFirst();
    requestRepresentation2 = requestsClient.getMany(exactMatch("id", uuidList.get(1))).getFirst();
    assertRequestDetails(requestRepresentation1, uuidList.get(0), itemBarcodePrefix+0);
    assertRequestDetails(requestRepresentation2, uuidList.get(1), itemBarcodePrefix+1);
    assertThat("printDetails should not be null for request1 because the request is printed",
      requestRepresentation1.getJsonObject("printDetails"), Matchers.notNullValue());
    assertThat("printDetails should not be null for request2 because the request is printed",
      requestRepresentation2.getJsonObject("printDetails"), Matchers.notNullValue());
    assertPrintDetails(requestRepresentation1, 1, "2024-06-25T11:54:07.000Z");
    assertPrintDetails(requestRepresentation2, 1, "2024-06-25T11:54:07.000Z");

    // Print both the request id again with same user.
    // In this case, we will get the latest print event date and the count is increased
    printRequest.put("printEventDate", "2024-06-25T14:54:07.000Z");
    restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    requestRepresentation1 = requestsClient.getMany(exactMatch("id", uuidList.get(0))).getFirst();
    requestRepresentation2 = requestsClient.getMany(exactMatch("id", uuidList.get(1))).getFirst();
    assertRequestDetails(requestRepresentation1, uuidList.get(0), itemBarcodePrefix+0);
    assertRequestDetails(requestRepresentation2, uuidList.get(1), itemBarcodePrefix+1);
    assertThat("printDetails should not be null for request1 because the request is printed",
      requestRepresentation1.getJsonObject("printDetails"), Matchers.notNullValue());
    assertThat("printDetails should not be null for request2 because the request is printed",
      requestRepresentation2.getJsonObject("printDetails"), Matchers.notNullValue());
    assertPrintDetails(requestRepresentation1, 2, "2024-06-25T14:54:07.000Z");
    assertPrintDetails(requestRepresentation2, 2, "2024-06-25T14:54:07.000Z");

    // Print only request1 with unknown user id.
    // In this case, request representation won't contain lastPrintRequester detail
    printRequest.put("requesterId", UUID.randomUUID());
    printRequest.put("requestIds", List.of(uuidList.get(0)));
    printRequest.put("printEventDate", "2024-06-25T14:59:07.000Z");
    restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    requestRepresentation1 = requestsClient.getMany(exactMatch("id", uuidList.get(0))).getFirst();
    requestRepresentation2 = requestsClient.getMany(exactMatch("id", uuidList.get(1))).getFirst();
    assertRequestDetails(requestRepresentation1, uuidList.get(0), itemBarcodePrefix+0);
    assertRequestDetails(requestRepresentation2, uuidList.get(1), itemBarcodePrefix+1);
    assertThat("printDetails should not be null for request1 because the request is printed",
      requestRepresentation1.getJsonObject("printDetails"), Matchers.notNullValue());
    assertThat("printDetails should not be null for request2 because the request is printed",
      requestRepresentation2.getJsonObject("printDetails"), Matchers.notNullValue());
    assertPrintDetails(requestRepresentation2, 2, "2024-06-25T14:54:07.000Z");
    assertThat(requestRepresentation1.getJsonObject("printDetails").getInteger("count"), is(3));
    assertThat(requestRepresentation1.getJsonObject("printDetails").getString("lastPrintedDate"),
      is("2024-06-25T14:59:07.000Z"));
    assertThat("lastPrintRequester object should be null because we used random uuid for user id while printing",
      requestRepresentation1.getJsonObject("printDetails").getJsonObject("lastPrintRequester"),
      Matchers.nullValue());
  }

  @Test
  void postPrintEventsWhenCirculationSettingIsNotPresentTest() {
    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", List.of(UUID.randomUUID()));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWhenDuplicateCirculationSettingFound() {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", true)));
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("Enable-Print-Event", false)));

    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", List.of(UUID.randomUUID()));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWhenPrintEventSettingIsDisable() {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", false)));

    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", List.of(UUID.randomUUID()));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWithInvalidRequestId() {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", true)));
    JsonObject printRequest = getPrintEvent();
    List<String> requestIds = new ArrayList<>(createMultipleRequests(10, "invalid-"));
    requestIds.add(UUID.randomUUID().toString());
    printRequest.put("requestIds", requestIds);
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }


  @Test
  void postPrintEventsWithInvalidField() {
    JsonObject printRequest = getPrintEvent();
    printRequest.put("invalidField", "invalid");
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWithInvalidField_EmptyRequestIdsList() {
    JsonObject printRequest = getPrintEvent();
    List<String> requestIds = List.of();
    printRequest.put("requestIds", requestIds);
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWithInvalidField_NullField() {
    JsonObject printRequest = getPrintEvent();
    printRequest.put("requesterId", null);
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  private JsonObject getPrintEvent() {
    return new JsonObject()
      .put("requesterId", "5f5751b4-e352-4121-adca-204b0c2aec43")
      .put("requesterName", "requester")
      .put("printEventDate", "2024-06-25T11:54:07.000Z");
  }

  private List<String> createMultipleRequests(int noOfRequests, String itemBarcode) {

    return IntStream.range(0, noOfRequests).mapToObj(i -> requestsFixture.place(
      new RequestBuilder()
        .open()
        .page()
        .forItem(itemsFixture.basedUponSmallAngryPlanet(itemBarcode + i))
        .by(userResource)
        .fulfillToHoldShelf()
        .withRequestExpiration(LocalDate.of(2024, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2024, 8, 15))
        .withPickupServicePointId(servicePointResource.getId())).getId().toString()).toList();
  }

  private void assertRequestDetails(JsonObject representation, String id, String barcodeName) {
    assertThat(representation.getString("id"), is(id));
    assertThat(representation.getString("requestType"), is("Page"));
    assertThat(representation.getString("requestLevel"), is("Item"));
    assertThat(representation.getString("requestDate"), is("2017-07-15T09:35:27.000Z"));
    assertThat(representation.getJsonObject("item").getString("barcode"), is(barcodeName));
    assertThat(representation.getString("fulfillmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2024-07-30T23:59:59.000Z"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2024-08-15"));
    assertThat(representation.getString("status"), is("Open - Not yet filled"));
    assertThat(representation.getString("pickupServicePointId"), is(servicePointResource.getId().toString()));
  }

  private void assertPrintDetails(JsonObject representation, int count, String printEventDate) {
    var printDetailObject = representation.getJsonObject("printDetails");
    var lastPrintRequesterObject = printDetailObject.getJsonObject("lastPrintRequester");
    assertThat(printDetailObject.getInteger("count"), is(count));
    assertThat(printDetailObject.getString("lastPrintedDate"), is(printEventDate));
    assertThat(lastPrintRequesterObject.getString("middleName"),
      is(userResource.getJson().getJsonObject("personal").getString("middleName")));
    assertThat(lastPrintRequesterObject.getString("lastName"),
      is(userResource.getJson().getJsonObject("personal").getString("lastName")));
    assertThat(lastPrintRequesterObject.getString("firstName"),
      is(userResource.getJson().getJsonObject("personal").getString("firstName")));
  }
}
