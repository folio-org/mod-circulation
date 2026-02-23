package org.folio.circulation.domain.notice.schedule;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
