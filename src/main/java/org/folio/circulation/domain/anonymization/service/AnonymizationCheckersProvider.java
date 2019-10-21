package org.folio.circulation.domain.anonymization.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.folio.circulation.domain.anonymization.checkers.AnonymizationChecker;
import org.folio.circulation.domain.anonymization.checkers.AnonymizeLoansImmediatelyChecker;
import org.folio.circulation.domain.anonymization.checkers.AnonymizeLoansWithFeeFinesImmediatelyChecker;
import org.folio.circulation.domain.anonymization.checkers.FeesAndFinesClosePeriodChecker;
import org.folio.circulation.domain.anonymization.checkers.LoanClosePeriodChecker;
import org.folio.circulation.domain.anonymization.checkers.NeverAnonymizeLoansChecker;
import org.folio.circulation.domain.anonymization.checkers.NeverAnonymizeLoansWithFeeFinesChecker;
import org.folio.circulation.domain.anonymization.checkers.NoAssociatedFeesAndFinesChecker;
import org.folio.circulation.domain.anonymization.config.LoanHistorySettings;

import com.google.inject.internal.util.Lists;

class AnonymizationCheckersProvider {

  private final LoanHistorySettings loanHistorySettings;
  private final List<AnonymizationChecker> generalCheckers;
  private final List<AnonymizationChecker> feesAndFinesCheckers;
  private final List<AnonymizationChecker> closedLoansCheckers;

  AnonymizationCheckersProvider(LoanHistorySettings settings) {
    this.loanHistorySettings = settings;
    generalCheckers = generalCheckers();
    feesAndFinesCheckers = feesAndFinesCheckers();
    closedLoansCheckers = closedLoansCheckers();
  }

  AnonymizationCheckersProvider() {
    this(null);
  }

  private List<AnonymizationChecker> generalCheckers() {
    return Collections.singletonList(new NoAssociatedFeesAndFinesChecker());
  }

  private List<AnonymizationChecker> closedLoansCheckers() {
    List<AnonymizationChecker> result = new ArrayList<>();
    if (loanHistorySettings == null) {
      return result;
    }

    switch (loanHistorySettings.getLoanClosingType()) {
    case IMMEDIATELY:
      result.add(new AnonymizeLoansImmediatelyChecker());
      break;
    case INTERVAL:
      result.add(new LoanClosePeriodChecker(loanHistorySettings.getLoanClosePeriod()));
      break;
    case UNKNOWN:
    case NEVER:
      result.add(new NeverAnonymizeLoansChecker());
      break;
    default:
      return result;
    }

    return result;
  }

  private List<AnonymizationChecker> feesAndFinesCheckers() {

    List<AnonymizationChecker> result = new ArrayList<>();
    if (loanHistorySettings == null || !loanHistorySettings.treatLoansWithFeesAndFinesDifferently()) {
      return result;
    }

    switch (loanHistorySettings.getFeesAndFinesClosingType()) {
    case IMMEDIATELY:
      result.add(new AnonymizeLoansWithFeeFinesImmediatelyChecker());
      break;
    case INTERVAL:
      result.add(new FeesAndFinesClosePeriodChecker(loanHistorySettings.getFeeFineClosePeriod()));
      break;
    case UNKNOWN:
    case NEVER:
      result.add(new NeverAnonymizeLoansWithFeeFinesChecker());
      break;
    default:
      return result;
    }

    return result;
  }

  List<AnonymizationChecker> getGeneralCheckers() {
    return generalCheckers;
  }

  List<AnonymizationChecker> getFeesAndFinesCheckers() {
    return feesAndFinesCheckers;
  }

  List<AnonymizationChecker> getClosedLoansCheckers() {
    return closedLoansCheckers;
  }
}