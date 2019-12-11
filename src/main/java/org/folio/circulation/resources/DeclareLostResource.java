package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.WebContext;

public class DeclareLostResource extends Resource {
  public DeclareLostResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    router.post("/circulation/loans/:id/declare-item-lost")
      .handler(this::declareLost);
  }

  private void declareLost(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final LoanRepository loanRepository = new LoanRepository(clients);

    validateDeclaredLostRequest(routingContext).thenCompose(r -> r.after(request ->
      loanRepository.getById(request.getLoanId())
        .thenCompose(loan -> loan.after(this::refuseWhenLoanIsClosed))
          .thenApply(declareItemLost(request))))
      .thenCompose(r-> r.after(loanRepository::updateLoanAndItemInStorage))
      .thenApply(NoContentResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private Function<Result<Loan>, Result<Loan>> declareItemLost(
    DeclareItemLostRequest request) {
    return x -> x.map(loan ->
      loan.declareItemLost(Objects.toString(request.getComment(), ""), request.getDeclaredLostDateTime()));
  }

  private CompletableFuture<Result<DeclareItemLostRequest>> validateDeclaredLostRequest(
    RoutingContext routingContext) {
    String loanId = routingContext.request().getParam("id");

    return completedFuture(
      DeclareItemLostRequest.from(routingContext.getBodyAsJson(), loanId));
  }

  private CompletableFuture<Result<Loan>> refuseWhenLoanIsClosed(Loan loan) {
    if (loan.isClosed()) {
      return completedFuture(
        failed(singleValidationError("Loan is closed", "id", loan.getId())));
    }
    return completedFuture(succeeded(loan));
  }
}
