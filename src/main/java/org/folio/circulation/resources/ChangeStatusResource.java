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

public abstract class ChangeStatusResource<T extends ChangeItemStatusRequest> extends Resource {
  public static final String COMMENT = "comment";
  private final String path;

  public ChangeStatusResource(HttpClient client, String path) {
    super(client);
    this.path = path;
  }

  public void register(Router router) {
    new RouteRegistration(path, router).create(this::changeItemStatus);
  }

  protected void changeItemStatus(RoutingContext routingContext) {
    createItemStatusChangeRequest(routingContext)
      .after(request -> changeItemStatus(request, routingContext))
      .thenApply(NoContentResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<Loan>> validate(Result<Loan> loanResult){
    return completedFuture(succeeded(loanResult))
      .thenApply(loan -> refuseWhenLoanIsClosed(loanResult))
      .thenApply(this::additionalValidation);
  }

  /**
   * Additional validation for a case. Note: validation whether the loan is closed
   * or not is executed within separate method.
   *
   * @param loanResult - loan
   * @return result.
   */
  protected Result<Loan> additionalValidation(Result<Loan> loanResult) {
    return loanResult;
  }

  protected abstract Loan changeLoanAndItemStatus(Loan loan, T request);

  protected abstract Result<T> createItemStatusChangeRequest(RoutingContext routingContext);

  private CompletableFuture<Result<Loan>> changeItemStatus(
    final T request, RoutingContext routingContext) {

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

  private Result<Loan> changeLoanAndItemStatus(Result<Loan> loanResult, T request) {
    return loanResult.next(loan -> Result.of(() ->
      changeLoanAndItemStatus(loan, request)));
  }
}
