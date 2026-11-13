package org.folio.circulation.resources.renewal;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.resources.context.RenewalContext;

final class RenewalPostRenewalProcessor {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private RenewalPostRenewalProcessor() {
  }

  static RenewalContext unsetDueDateChangedByRecallIfNoOpenRecallsInQueue(
    RenewalContext renewalContext) {

    Loan loan = renewalContext.getLoan();

    boolean noOpenRecallForLoan = renewalContext.getRequestQueue()
      .getRequests()
      .stream()
      .filter(Request::isRecall)
      .filter(Request::isNotYetFilled)
      .noneMatch(request -> request.isFor(loan));

    if (loan.wasDueDateChangedByRecall() && noOpenRecallForLoan) {
      log.info("unsetDueDateChangedByRecallIfNoOpenRecallsInQueue:: unsetting due date " +
        "changed by recall for loan {}", loan::getId);
      return renewalContext.withLoan(loan.unsetDueDateChangedByRecall());
    }

    return renewalContext;
  }
}
