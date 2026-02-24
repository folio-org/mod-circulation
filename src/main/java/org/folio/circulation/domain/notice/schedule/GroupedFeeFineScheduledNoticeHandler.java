package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineChargeNoticeContextWithoutUser;

import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.Clients;

import io.vertx.core.json.JsonObject;

public class GroupedFeeFineScheduledNoticeHandler extends GroupedScheduledNoticeHandler {

  public GroupedFeeFineScheduledNoticeHandler(Clients clients, LoanRepository loanRepository) {
    super(clients, new FeeFineScheduledNoticeHandler(clients, loanRepository), "feeCharges");
  }

  @Override
  protected JsonObject buildNoticeContext(ScheduledNoticeContext context) {
    return createFeeFineChargeNoticeContextWithoutUser(context.getAccount(), context.getLoan(),
      context.getChargeAction());
  }
}
