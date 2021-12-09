package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class TitleLevelRequestScheduledNoticeHandler extends RequestScheduledNoticeHandler {

  public TitleLevelRequestScheduledNoticeHandler(Clients clients, ItemRepository itemRepository) {
    super(clients, itemRepository);
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(
    ScheduledNoticeContext context) {

    return ofAsync(() -> context)
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchRequest));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> fetchRequest(
    ScheduledNoticeContext context) {

    return requestRepository.getByIdWithoutItem(context.getNotice().getRequestId())
      .thenApply(mapResult(context::withRequest))
      .thenApply(this::failWhenTitleLevelRequestIsIncomplete);
  }
}
