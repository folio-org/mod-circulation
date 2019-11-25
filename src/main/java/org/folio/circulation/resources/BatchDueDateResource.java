package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.UpdateLoan;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.JsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resource for changing due date via batch.
 * URL: '/circulation/batch/change-due-date'
 */
public class BatchDueDateResource extends Resource {

  private final String rootPath;

  public BatchDueDateResource(HttpClient client, String rootPath) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::changeDueDate);
  }

  private void changeDueDate(RoutingContext routingContext) {
    JsonObject body = routingContext.getBodyAsJson();
    JsonArray loans = body.getJsonArray("loans");
    String dueDateValue = body.getString("dueDate");

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    LoanRepository loanRepository = new LoanRepository(clients);

    UpdateLoan updateLoan = new UpdateLoan(clients, loanRepository);

    Map<UUID, CompletableFuture<Result<Loan>>> loanIdsAndFutures = new HashMap<>();

    JsonObject responseBody = new JsonObject();

    loans.stream().forEach(loanId -> loanIdsAndFutures.put(UUID.fromString(String.valueOf(loanId)),
      loanRepository.getById(String.valueOf(loanId))));

    verifyErrorsWhileRetrievingLoans(loanIdsAndFutures, responseBody)
      .thenAccept(e -> loanIdsAndFutures.forEach((key, value) -> value
        .thenApply(loan -> loan.value()
          .changeAction("dueDateChange")
          .changeDueDate(new DateTime(dueDateValue)))
        .thenApply(u -> updateLoan.replaceLoan(u)
          .thenAccept(z -> {
            if (z.failed()) {
              updateResponseBodyByError(responseBody, key, "Error while updating loan");
            }
          }).thenApply(c -> new JsonResponseResult(200, responseBody, null))
          .thenAccept(n -> n.writeTo(routingContext.response())))));
  }

  private CompletableFuture<Void> verifyErrorsWhileRetrievingLoans(Map<UUID, CompletableFuture<Result<Loan>>> futures,
                                                                   JsonObject responseBody) {
    return CompletableFuture
      .allOf(new ArrayList<>(futures.values()).toArray(new CompletableFuture[futures.size()]))
      .thenAccept(e -> futures.forEach((key, value) -> value.thenAccept(r -> {
        if (r.failed()) {
          updateResponseBodyByError(responseBody, key, "Error while retrieving loan");
        }
      })));
  }

  private void updateResponseBodyByError(JsonObject resultBody, UUID loanId, String errorMessage){
    resultBody.put(String.valueOf(loanId), errorMessage);
  }
}
