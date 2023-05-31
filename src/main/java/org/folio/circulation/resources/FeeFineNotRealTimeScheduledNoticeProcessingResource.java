package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST_FINE_CHARGED;

import java.util.EnumSet;

import org.folio.circulation.domain.notice.schedule.GroupedFeeFineScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.GroupedScheduledNoticeHandler;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;

import io.vertx.core.http.HttpClient;

public class FeeFineNotRealTimeScheduledNoticeProcessingResource
  extends GroupingScheduledNoticeProcessingResource {

  public FeeFineNotRealTimeScheduledNoticeProcessingResource(HttpClient client) {
    super(client, "/circulation/fee-fine-not-real-time-scheduled-notices-processing",
      EnumSet.of(AGED_TO_LOST_FINE_CHARGED), false);
  }

  @Override
  protected GroupedScheduledNoticeHandler getHandler(Clients clients, LoanRepository loanRepository) {
    return new GroupedFeeFineScheduledNoticeHandler(clients, loanRepository);
  }

}
