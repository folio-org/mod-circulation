package api.support.fixtures;

import static api.support.RestAssuredClient.from;
import static api.support.RestAssuredClient.post;
import static api.support.http.InterfaceUrls.endSessionUrl;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class EndPatronSessionClient {

  private static final String REQUEST_ID = "end-patron-session-request";


  public void endCheckOutSession(UUID patronId) {
    endPatronSession(patronId, "Check-out");
  }

  public void endCheckInSession(UUID patronId) {
    endPatronSession(patronId, "Check-in");
  }

  private void endPatronSession(UUID patronId, String actionType) {
    JsonObject body = new JsonObject()
      .put("patronId", patronId.toString())
      .put("actionType", actionType);

    endPatronSession(body);
  }

  private void endPatronSession(JsonObject body) {
    attemptEndPatronSession(body, 204);
  }

  public Response attemptEndPatronSession(JsonObject body) {
    return attemptEndPatronSession(body, 422);
  }

  private Response attemptEndPatronSession(JsonObject body, int expectedStatus) {
    return from(post(body, endSessionUrl(), expectedStatus, REQUEST_ID));
  }
}
