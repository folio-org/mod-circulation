package org.folio.circulation.resources;

import java.util.Objects;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.representations.DeclareItemLostRequest;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

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
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);

    validateDeclaredLostRequest(routingContext).after(request ->
      loanRepository.getById(request.getLoanId())
        .thenApply(LoanValidator::refuseWhenLoanIsClosed)
        .thenApply(loan -> declareItemLost(loan, request)))
      .thenApply(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage))
      .thenCompose(r -> r.thenApply(NoContentResult::from))
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private Result<Loan> declareItemLost(Result<Loan> loanResult,
    DeclareItemLostRequest request) {

    return loanResult.next(loan -> Result.of(() ->
      loan.declareItemLost(
        Objects.toString(request.getComment(), ""),
        request.getDeclaredLostDateTime())));
  }

  private Result<DeclareItemLostRequest> validateDeclaredLostRequest(
    RoutingContext routingContext) {

    String loanId = routingContext.request().getParam("id");
    return DeclareItemLostRequest.from(routingContext.getBodyAsJson(), loanId);
  }
}
