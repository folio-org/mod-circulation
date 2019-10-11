package org.folio.circulation.domain.anonymization;

import java.util.List;

import org.folio.circulation.domain.anonymization.checks.AnonymizationChecker;
import org.folio.circulation.domain.anonymization.checks.AnonymizeLoansImmediatelyChecker;
import org.folio.circulation.domain.anonymization.checks.AnonymizeLoansWithFeeFinesImmediatelyChecker;
import org.folio.circulation.domain.anonymization.checks.FeesAndFinesClosePeriodChecker;
import org.folio.circulation.domain.anonymization.checks.HaveNoAssociatedFeesAndFinesChecker;
import org.folio.circulation.domain.anonymization.checks.LoanClosePeriodChecker;
import org.folio.circulation.domain.anonymization.checks.NeverAnonymizeLoansChecker;
import org.folio.circulation.domain.anonymization.checks.NeverAnonymizeLoansWithFeeFinesChecker;
import org.folio.circulation.domain.anonymization.config.LoanHistorySettings;

import com.google.inject.internal.util.Lists;

class AnonymizationCheckersProvider {

  private LoanHistorySettings config;

  AnonymizationCheckersProvider(LoanHistorySettings config) {
    this.config = config;
  }

  AnonymizationCheckersProvider() {
  }

  List<AnonymizationChecker> getLoanAnonymizationCheckers() {
    List<AnonymizationChecker> checkers = Lists.newArrayList();

    if (config == null) {
      checkers.add(new HaveNoAssociatedFeesAndFinesChecker());
      return checkers;
    }

    if (config.treatLoansWithFeesAndFinesDifferently()) {
      checkers.addAll(getFeesAndFinesCheckers());
    } else {
      checkers.addAll(getClosedLoansCheckers());
    }
    return checkers;
  }

  private List<AnonymizationChecker> getClosedLoansCheckers() {
    List<AnonymizationChecker> result = Lists.newArrayList();

    switch (config.getLoanClosingType()) {
    case IMMEDIATELY:
      result.add(new AnonymizeLoansImmediatelyChecker());
      break;
    case INTERVAL:
      result.add(new LoanClosePeriodChecker(config.getLoanClosePeriod()));
      break;
    case UNKNOWN:
    case NEVER:
      result.add(new NeverAnonymizeLoansChecker());
      break;
    default:
    }

    return result;
  }

  private List<AnonymizationChecker> getFeesAndFinesCheckers() {
    List<AnonymizationChecker> result = Lists.newArrayList();

    switch (config.getFeesAndFinesClosingType()) {
    case IMMEDIATELY:
      result.add(new AnonymizeLoansWithFeeFinesImmediatelyChecker());
      break;
    case INTERVAL:
      result.add(new FeesAndFinesClosePeriodChecker(config.getFeeFineClosePeriod()));
      break;
    case UNKNOWN:
    case NEVER:
      result.add(new NeverAnonymizeLoansWithFeeFinesChecker());
    }

    return result;
  }

}
