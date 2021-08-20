package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.endSessionUrl;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class EndPatronSessionClient {

  private static final String REQUEST_ID = "end-patron-session-request";


  public void endCheckOutSession(UUID patronId) {
    endPatronSession(patronId, "Check-out");
  }

  public void endCheckInSession(UUID patronId) {
    endPatronSession(patronId, "Check-in");
  }

  private void endPatronSession(UUID patronId, String actionType) {
    JsonObject body = new JsonObject()
      .put(PATRON_ID, patronId.toString())
      .put(ACTION_TYPE, actionType);
    JsonArray jsonArray = new JsonArray().add(body);
    endPatronSession(new JsonObject().put("endSessions", jsonArray));
  }

  private void endPatronSession(JsonObject body) {
    attemptEndPatronSession(body, 204);
  }

  public Response attemptEndPatronSession(JsonObject body) {
    return attemptEndPatronSession(body, 422);
  }

  private Response attemptEndPatronSession(JsonObject body, int expectedStatus) {
    return new RestAssuredClient(getOkapiHeadersFromContext())
      .post(body, endSessionUrl(), expectedStatus, REQUEST_ID);
  }
}
