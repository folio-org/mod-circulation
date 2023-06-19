package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.LoanAction.CHECKED_OUT;
import static org.folio.circulation.domain.LoanAction.CHECKED_OUT_THROUGH_OVERRIDE;
import static org.folio.circulation.domain.LoanAction.CLAIMED_RETURNED;
import static org.folio.circulation.domain.LoanAction.CLOSED_LOAN;
import static org.folio.circulation.domain.LoanAction.DECLARED_LOST;
import static org.folio.circulation.domain.LoanAction.DUE_DATE_CHANGED;
import static org.folio.circulation.domain.LoanAction.ITEM_AGED_TO_LOST;
import static org.folio.circulation.domain.LoanAction.MISSING;
import static org.folio.circulation.domain.LoanAction.RECALLREQUESTED;
import static org.folio.circulation.domain.LoanAction.RENEWED;
import static org.folio.circulation.domain.LoanAction.RENEWED_THROUGH_OVERRIDE;
import static org.folio.circulation.domain.LoanAction.PATRON_INFO_ADDED;
import static org.folio.circulation.domain.LoanAction.STAFF_INFO_ADDED;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LogContextActionResolver {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final HashMap<String, String> loanLogActions = new HashMap<>();

  static {
    loanLogActions.put(DECLARED_LOST.getValue(), "Declared lost");
    loanLogActions.put(RENEWED.getValue(), "Renewed");
    loanLogActions.put(RENEWED_THROUGH_OVERRIDE.getValue(), "Renewed through override");
    loanLogActions.put(CHECKED_OUT.getValue(), "Checked out");
    loanLogActions.put(CHECKED_OUT_THROUGH_OVERRIDE.getValue(), "Checked out through override");
    loanLogActions.put(RECALLREQUESTED.getValue(), "Recall requested");
    loanLogActions.put(CLAIMED_RETURNED.getValue(), "Claimed returned");
    loanLogActions.put(MISSING.getValue(), "Marked as missing");
    loanLogActions.put(CLOSED_LOAN.getValue(), "Closed loan");
    loanLogActions.put(ITEM_AGED_TO_LOST.getValue(), "Age to lost");
    loanLogActions.put(DUE_DATE_CHANGED.getValue(), "Changed due date");
    loanLogActions.put(PATRON_INFO_ADDED.getValue(), "Patron info added");
    loanLogActions.put(STAFF_INFO_ADDED.getValue(), "Staff info added");
  }

  public static String resolveAction(String action) {
    log.debug("resolveAction:: parameters action: {}", action);

    return ofNullable(loanLogActions.get(action))
      .orElse("");
  }

  private LogContextActionResolver() {
  }
}
