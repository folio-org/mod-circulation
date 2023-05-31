package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.DUE_DATE;

import java.util.EnumSet;

import org.folio.circulation.domain.notice.schedule.GroupedLoanScheduledNoticeHandler;
import org.folio.circulation.domain.notice.schedule.GroupedScheduledNoticeHandler;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;

import io.vertx.core.http.HttpClient;

public class DueDateNotRealTimeScheduledNoticeProcessingResource
  extends GroupingScheduledNoticeProcessingResource {

  public DueDateNotRealTimeScheduledNoticeProcessingResource(HttpClient client) {
    super(client, "/circulation/due-date-not-real-time-scheduled-notices-processing",
      EnumSet.of(DUE_DATE), false);
  }

  @Override
  protected GroupedScheduledNoticeHandler getHandler(Clients clients, LoanRepository loanRepository) {
    return new GroupedLoanScheduledNoticeHandler(clients, loanRepository);
  }

}
