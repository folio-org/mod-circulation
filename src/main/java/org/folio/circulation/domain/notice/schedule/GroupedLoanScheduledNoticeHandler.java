package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContextWithoutUser;

import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;

import io.vertx.core.json.JsonObject;

public class GroupedLoanScheduledNoticeHandler extends GroupedScheduledNoticeHandler {

  public GroupedLoanScheduledNoticeHandler(Clients clients, LoanRepository loanRepository) {
    super(clients, new LoanScheduledNoticeHandler(clients, loanRepository), "loans");
  }

  @Override
  protected JsonObject buildNoticeContext(ScheduledNoticeContext context) {
    return createLoanNoticeContextWithoutUser(context.getLoan());
  }
}
