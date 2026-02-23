package org.folio.circulation.domain.notice;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.circulation.domain.notice.combiner.SingleNoticeContextCombiner;
import org.folio.circulation.support.Clients;

public class SingleImmediatePatronNoticeService extends ImmediatePatronNoticeService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public SingleImmediatePatronNoticeService(Clients clients) {
    super(clients, new SingleNoticeContextCombiner());
  }
}
