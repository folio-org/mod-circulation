package api.printEvents;

import api.support.APITests;
import api.support.builders.CirculationSettingBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static api.support.http.InterfaceUrls.printEventsUrl;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;

class PrintEventsTests extends APITests {


  @Test
  void postPrintEventsTest() {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("Enable print event log")
      .withValue(new JsonObject().put("Enable Print Event", true)));
    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", createOneHundredRequests());
    System.out.println(printRequest.getString("requestIds"));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_CREATED));
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
      .withName("Enable print event log")
      .withValue(new JsonObject().put("Enable Print Event", true)));
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("Enable print event log")
      .withValue(new JsonObject().put("Enable-Print-Event", false)));

    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", List.of(UUID.randomUUID()));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWhenPrintEventSettingIsDisable() {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("Enable print event log")
      .withValue(new JsonObject().put("Enable Print Event", false)));

    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", List.of(UUID.randomUUID()));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWithInvalidRequestId() {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("Enable print event log")
      .withValue(new JsonObject().put("Enable Print Event", true)));
    JsonObject printRequest = getPrintEvent();
    List<UUID> requestIds = createOneHundredRequests();
    requestIds.add(UUID.randomUUID());
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
      .put("printEventDate", "2024-06-25T14:30:00Z");
  }

  private List<UUID> createOneHundredRequests() {
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    return IntStream.range(0, 100).mapToObj(notUsed -> requestsFixture.place(
        new RequestBuilder()
          .open()
          .page()
          .forItem(itemsFixture.basedUponSmallAngryPlanet())
          .by(usersFixture.charlotte())
          .fulfillToHoldShelf()
          .withPickupServicePointId(pickupServicePointId)).getId()).toList();
  }
}
