package api.printEvents;

import api.support.APITests;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

 class PrintEventsTests extends APITests {
  public static final String REQUEST_IDS_FIELD = "requestIds";
  public static final String REQUESTER_ID_FIELD = "requesterId";
  public static final String REQUESTER_NAME_FIELD = "requesterName";
  public static final String PRINT_DATE_FIELD = "printEventDate";

  @Test
  void postPrintEventsTest() {
    JsonObject printRequest = getPrintEvent();
    Response response = printEventsClient.attemptCreate(printRequest);
    assertThat(response.getStatusCode(), Is.is(HttpURLConnection.HTTP_CREATED));
  }
   private JsonObject getPrintEvent() {
     List<String> requestIds = List.of("request1", "request2");
     return new JsonObject()
       .put(REQUEST_IDS_FIELD, requestIds)
       .put(REQUESTER_ID_FIELD, "sample")
       .put(REQUESTER_NAME_FIELD, "Sample Requester")
       .put(PRINT_DATE_FIELD, "2024-06-25T14:30:00Z");
   }
}
