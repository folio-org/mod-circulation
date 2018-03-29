package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.UserFetcher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonHttpResult;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import java.util.UUID;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class CheckOutResource extends CollectionResource {
  public CheckOutResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/circulation/check-out", router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final UserFetcher userFetcher = new UserFetcher(clients);

    JsonObject request = routingContext.getBodyAsJson();

    final JsonObject loan = new JsonObject();
    loan.put("id", UUID.randomUUID().toString());
    loan.put("status", new JsonObject().put("name", "Open"));

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(loan)))
      .thenCombineAsync(userFetcher.getUserByBarcode(request.getString("userBarcode")), this::addUser)
      .thenApply(r -> r.map(toLoan()))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private Function<LoanAndRelatedRecords, JsonObject> toLoan() {
    return loanAndRelatedRecords -> {
      final JsonObject loan = loanAndRelatedRecords.getLoan();

      loan.put("userId", loanAndRelatedRecords.requestingUser.getString("id"));

      return loan;
    };
  }

  private HttpResult<LoanAndRelatedRecords> addUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<JsonObject> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }
}
