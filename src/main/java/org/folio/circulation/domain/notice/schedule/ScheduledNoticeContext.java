package org.folio.circulation.domain.notice.schedule;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;

@With
@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class ScheduledNoticeContext {
  private final ScheduledNotice notice;
  private Account account;
  private FeeFineAction currentAction;
  private FeeFineAction chargeAction;
  private Loan loan;
  private Request request;
  private String patronNoticePolicyId;
  private boolean lostItemFeesForAgedToLostNoticeExist;
  private JsonObject noticeContext;
  private NoticeLogContextItem noticeLogContextItem;
}
