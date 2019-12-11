package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.WebContext;

public class DeclareLostResource extends Resource {
  public DeclareLostResource(HttpClient client) {
    super(client);
  }

  private ItemRepository itemRepository;
  private LoanRepository loanRepository;

  @Override
  public void register(Router router) {
    router.post("/circulation/loans/:id/declare-item-lost")
      .handler(this::declareLost);
  }

  private void declareLost(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    loanRepository = new LoanRepository(clients);
    itemRepository = new ItemRepository(clients, true, true, true);

    validateDeclaredLostRequest(routingContext).after(request ->
      loanRepository.getById(request.getLoanId())
        .thenApply(this::refuseWhenLoanIsClosed)
        .thenApply(loan -> declareItemLost(loan, request)))
      .thenApply(this::updateLoanAndItemInStorage)
      .thenCompose(r -> r.thenApply(NoContentResult::from))
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private Result<Loan> declareItemLost(Result<Loan> loanResult,
    DeclareItemLostRequest request) {

    return loanResult.next(loan -> {
      Loan loan1 = loan
        .declareItemLost(Objects.toString(request.getComment(), ""),
          request.getDeclaredLostDateTime());
      return Result.of(() -> loan1);
    });
  }

  private Result<DeclareItemLostRequest> validateDeclaredLostRequest(
    RoutingContext routingContext) {

    String loanId = routingContext.request().getParam("id");
    return DeclareItemLostRequest.from(routingContext.getBodyAsJson(), loanId);
  }

  private Result<Loan> refuseWhenLoanIsClosed(Result<Loan> loanResult) {

    return loanResult.next(loan -> {
      if (loan.isClosed()) {
        return failed(
          singleValidationError("Loan is closed", "id", loan.getId()));
      }
      return succeeded(loan);
    });
  }

  private CompletableFuture<Result<Loan>> updateLoanAndItemInStorage(
    Result<Loan> loanResult) {

    return loanResult.after(loan -> {
      if (loan == null || loan.getItem() == null) {
        return null;
      }
      return itemRepository.updateItem(loan.getItem())
        .thenCompose(x -> loanRepository.updateLoan(loan));
    });
  }
}
