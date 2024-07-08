package api.printEvents;

import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static api.support.http.InterfaceUrls.printEventsUrl;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;

class PrintEventsTests extends APITests {

  @Test
  void postPrintEventsTest() {
    JsonObject printRequest = getPrintEvent();
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/create-batch"), "post-print-event");
    assertThat(response, hasStatus(HTTP_CREATED));
  }

  @Test
  void postPrintEventsWithInvalidField() {
    JsonObject printRequest = getPrintEvent();
    printRequest.put("invalidField", "invalid");
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/create-batch"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWithInvalidField_EmptyRequestIdsList() {
    JsonObject printRequest = getPrintEvent();
    List<String> requestIds = List.of();
    printRequest.put("requestIds", requestIds);
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/create-batch"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWithInvalidField_NullField() {
    JsonObject printRequest = getPrintEvent();
    printRequest.put("requesterId", null);
    Response response = restAssuredClient.post(printRequest, printEventsUrl("/create-batch"), "post-print-event");
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  private JsonObject getPrintEvent() {
    List<String> requestIds = List.of("5f5751b4-e352-4121-adca-204b0c2aec43", "5f5751b4-e352-4121-adca-204b0c2aec44");
    return new JsonObject()
      .put("requestIds", requestIds)
      .put("requesterId", "5f5751b4-e352-4121-adca-204b0c2aec43")
      .put("requesterName", "requester")
      .put("printEventDate", "2024-06-25T14:30:00Z");
  }
}
