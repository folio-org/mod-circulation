package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.declareLoanItemLostURL;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;

import api.support.RestAssuredClient;
import api.support.builders.DeclareItemLostRequestBuilder;
import io.vertx.core.json.JsonObject;

public class DeclareLostFixtures {
  private final RestAssuredClient restAssuredClient;

  public DeclareLostFixtures() {
    this.restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public Response declareItemLost(DeclareItemLostRequestBuilder builder) {

    JsonObject request = builder.create();
    return restAssuredClient.post(request, declareLoanItemLostURL(builder.getLoanId()),
      204, "declare-item-lost-request");
  }

  public Response attemptDeclareItemLost(DeclareItemLostRequestBuilder builder) {

    JsonObject request = builder.create();

    return restAssuredClient.post(request, declareLoanItemLostURL(builder.getLoanId()),
      "attempt-declare-item-lost-request");
  }

  public Response declareItemLost(JsonObject loanJson) {
    final UUID loanId = UUID.fromString(loanJson.getString("id"));

    return declareItemLost(loanId);
  }

  public Response declareItemLost(UUID loanId) {
    final DeclareItemLostRequestBuilder builder = new DeclareItemLostRequestBuilder()
      .forLoanId(loanId)
      .on(ClockUtil.getZonedDateTime())
      //creating "real" servicepoint data here would require a lot of setup code to
      //initialize a ResourceClient, the intialize a service point creator, and
      //so on.  As this is a convenience function that's only used when the loan
      //settings are not integral to the test, it is easier to supply dummy data.
      .withServicePointId(UUID.randomUUID())
      .withComment("testing");

    return declareItemLost(builder);
  }
}
