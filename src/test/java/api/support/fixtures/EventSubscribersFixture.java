package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;
import static api.support.RestAssuredClient.defaultRestAssuredClient;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import io.vertx.core.json.JsonObject;

public class EventSubscribersFixture {
  private final RestAssuredClient restAssuredClient = defaultRestAssuredClient();

  public void publishLoanRelatedFeeFineClosedEvent(UUID loanId) {
    final Response response = attemptPublishLoanRelatedFeeFineClosedEvent(loanId,
      UUID.randomUUID());

    assertThat(response.getStatusCode(), is(204));
  }

  public Response attemptPublishLoanRelatedFeeFineClosedEvent(UUID loanId, UUID accountId) {
    final JsonObject payload = new JsonObject();
    write(payload, "feeFineId", accountId);
    write(payload, "loanId", loanId);

    return restAssuredClient.post(payload,
      circulationModuleUrl("/circulation/handlers/loan-related-fee-fine-closed"),
      "loan-related-fee-fine-closed-event");
  }
}
