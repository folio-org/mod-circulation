package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.validation.LoanValidator.refuseWhenLoanIsClosed;
import static org.folio.circulation.support.ItemRepository.noLocationMaterialTypeAndLoanTypeInstance;
import static org.folio.circulation.support.Result.succeeded;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import java.util.concurrent.CompletableFuture;

public abstract class ChangeStatusResource extends Resource {
  public static final String COMMENT = "comment";

  public ChangeStatusResource(HttpClient client) {
    super(client);
  }

  public void register(Router router, String path) {
    new RouteRegistration(path, router)
      .create(this::changeItemStatus);
  }

  protected void changeItemStatus(RoutingContext routingContext) {
    createItemStatusChangeRequest(routingContext)
      .after(request -> changeItemStatus(request, routingContext))
      .thenApply(NoContentResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  protected CompletableFuture<Result<Loan>> validate(Result<Loan> loanResult){
    return completedFuture(succeeded(loanResult))
      .thenApply(loan -> refuseWhenLoanIsClosed(loanResult));
  }

  protected abstract Loan changeLoanAndItemStatus(Loan loan, ChangeItemStatusRequest request);

  protected abstract Result<ChangeItemStatusRequest> createItemStatusChangeRequest(
    RoutingContext routingContext);

  private CompletableFuture<Result<Loan>> changeItemStatus(
    final ChangeItemStatusRequest request, RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = noLocationMaterialTypeAndLoanTypeInstance(clients);
    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);

    return succeeded(request)
      .after(req -> loanRepository.getById(req.getLoanId()))
      .thenCompose(this::validate)
      .thenApply(loan -> changeLoanAndItemStatus(loan, request))
      .thenCompose(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage));
  }

  private Result<Loan> changeLoanAndItemStatus(Result<Loan> loanResult,
    ChangeItemStatusRequest request) {

    return loanResult.next(loan -> Result.of(() ->
      changeLoanAndItemStatus(loan, request)));
  }
}
