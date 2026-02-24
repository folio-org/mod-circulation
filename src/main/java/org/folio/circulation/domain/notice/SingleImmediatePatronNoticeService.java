package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.notice.combiner.SingleNoticeContextCombiner;
import org.folio.circulation.support.Clients;

public class SingleImmediatePatronNoticeService extends ImmediatePatronNoticeService {

  public SingleImmediatePatronNoticeService(Clients clients) {
    super(clients, new SingleNoticeContextCombiner());
  }
}
