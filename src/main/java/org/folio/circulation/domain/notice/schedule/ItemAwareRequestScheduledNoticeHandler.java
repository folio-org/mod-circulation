package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class ItemAwareRequestScheduledNoticeHandler extends RequestScheduledNoticeHandler {

  public ItemAwareRequestScheduledNoticeHandler(Clients clients,
    RequestRepository requestRepository, LoanRepository loanRepository) {

    super(clients, loanRepository, requestRepository);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(
    ScheduledNoticeContext context) {

    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchRequestRelatedRecords))
      .thenCompose(r -> r.after(this::fetchPatronNoticePolicyIdForRequest));
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchRequestRelatedRecords(
    ScheduledNoticeContext context) {

    return super.fetchRequestRelatedRecords(context)
      .thenApply(r -> r.next(this::failWhenRequestHasNoItem));
  }

}
