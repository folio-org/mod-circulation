package org.folio.circulation.domain.notice.schedule;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContextWithoutUser;

import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;

import io.vertx.core.json.JsonObject;

public class GroupedLoanScheduledNoticeHandler extends GroupedScheduledNoticeHandler {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());


  public GroupedLoanScheduledNoticeHandler(Clients clients, LoanRepository loanRepository) {
    log.debug("GroupedLoanScheduledNoticeHandler:: initializing grouped loan scheduled notice handler");
    super(clients, new LoanScheduledNoticeHandler(clients, loanRepository), "loans");
  }

  @Override
  protected JsonObject buildNoticeContext(ScheduledNoticeContext context) {
    return createLoanNoticeContextWithoutUser(context.getLoan());
  }
}
