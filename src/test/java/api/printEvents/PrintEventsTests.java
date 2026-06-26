package api.printEvents;

import static api.support.http.InterfaceUrls.printEventsUrl;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.APITests;
import api.support.builders.CirculationSettingBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

class PrintEventsTests extends APITests {

  @ParameterizedTest
  @MethodSource("api.support.utl.BooleanArgumentProvider#provideTrueValues")
  void postPrintEventsTest(Object trueValue) {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", trueValue)));
    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", createOneHundredRequests());
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_NO_CONTENT));
  }

  @Test
  void postPrintEventsWhenCirculationSettingIsNotPresentTest() {
    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", List.of(UUID.randomUUID()));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @ParameterizedTest
  @MethodSource("api.support.utl.BooleanArgumentProvider#provideTrueAndFalseValues")
  void postPrintEventsWhenDuplicateCirculationSettingFound(Object trueValue, Object falseValue) {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", trueValue)));
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("Enable-Print-Event", falseValue)));

    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", List.of(UUID.randomUUID()));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @ParameterizedTest
  @MethodSource("api.support.utl.BooleanArgumentProvider#provideFalseValues")
  void postPrintEventsWhenPrintEventSettingIsDisable(Object falseValue) {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", falseValue)));

    JsonObject printRequest = getPrintEvent();
    printRequest.put("requestIds", List.of(UUID.randomUUID()));
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/print-events-entry"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @ParameterizedTest
  @MethodSource("api.support.utl.BooleanArgumentProvider#provideTrueValues")
  void postPrintEventsWithInvalidRequestId(Object trueValue) {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("printEventLogFeature")
      .withValue(new JsonObject().put("enablePrintLog", trueValue)));
    JsonObject printRequest = getPrintEvent();
    List<UUID> requestIds = new ArrayList<>(createOneHundredRequests());
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
