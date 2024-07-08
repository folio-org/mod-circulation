package api.printEvents;

import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;

class PrintEventsTests extends APITests {
  public static final String REQUEST_IDS_FIELD = "requestIds";
  public static final String REQUESTER_ID_FIELD = "requesterId";
  public static final String REQUESTER_NAME_FIELD = "requesterName";
  public static final String PRINT_DATE_FIELD = "printEventDate";
  public static final String INVALID_FIELD = "invalidField";

  @Test
  void postPrintEventsTest() {
    JsonObject printRequest = getPrintEvent();
    Response response = printEventsClient.attemptCreate(printRequest);
    assertThat(response, hasStatus(HTTP_CREATED));
  }

  @Test
  void postPrintEventsWithInvalidField() {
    JsonObject printRequest = getPrintEvent();
    printRequest.put(INVALID_FIELD, "invalid");
    Response response = printEventsClient.attemptCreate(printRequest);
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWithInvalidField_EmptyRequestIdsList() {
    JsonObject printRequest = getPrintEvent();
    List<String> requestIds = List.of();
    printRequest.put(REQUEST_IDS_FIELD, requestIds);
    Response response = printEventsClient.attemptCreate(printRequest);
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  @Test
  void postPrintEventsWithInvalidField_NullField() {
    JsonObject printRequest = getPrintEvent();
    printRequest.put(REQUESTER_ID_FIELD, null);
    Response response = printEventsClient.attemptCreate(printRequest);
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
  }

  private JsonObject getPrintEvent() {
    List<String> requestIds = List.of("request1", "request2");
    return new JsonObject()
      .put(REQUEST_IDS_FIELD, requestIds)
      .put(REQUESTER_ID_FIELD, "sreeja")
      .put(REQUESTER_NAME_FIELD, "Sample Requester")
      .put(PRINT_DATE_FIELD, "2024-06-25T14:30:00Z");
  }
}
