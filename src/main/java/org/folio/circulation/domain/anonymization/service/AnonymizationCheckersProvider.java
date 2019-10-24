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
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfigurationForTenant;

class AnonymizationCheckersProvider {

  private final LoanAnonymizationConfigurationForTenant loanAnonymizationConfigurationForTenant;
  private final List<AnonymizationChecker> generalCheckers;
  private final List<AnonymizationChecker> feesAndFinesCheckers;
  private final List<AnonymizationChecker> closedLoansCheckers;

  AnonymizationCheckersProvider(LoanAnonymizationConfigurationForTenant settings) {
    this.loanAnonymizationConfigurationForTenant = settings;
    generalCheckers = getDefaultCheckers();
    feesAndFinesCheckers = getFeesAndFinesCheckersFromLoanHistory();
    closedLoansCheckers = getClosedLoansCheckersFromLoanHistory();
  }

  AnonymizationCheckersProvider() {
    this(null);
  }

  private List<AnonymizationChecker> getDefaultCheckers() {
    return Collections.singletonList(new NoAssociatedFeesAndFinesChecker());
  }

  private List<AnonymizationChecker> getClosedLoansCheckersFromLoanHistory() {
    List<AnonymizationChecker> result = new ArrayList<>();
    if (loanAnonymizationConfigurationForTenant == null) {
      return result;
    }

    switch (loanAnonymizationConfigurationForTenant.getLoanClosingType()) {
    case IMMEDIATELY:
      result.add(new AnonymizeLoansImmediatelyChecker());
      break;
    case INTERVAL:
      result.add(new LoanClosePeriodChecker(
          loanAnonymizationConfigurationForTenant.getLoanClosePeriod()));
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

  private List<AnonymizationChecker> getFeesAndFinesCheckersFromLoanHistory() {

    List<AnonymizationChecker> result = new ArrayList<>();
    if (loanAnonymizationConfigurationForTenant == null || !loanAnonymizationConfigurationForTenant
        .treatLoansWithFeesAndFinesDifferently()) {
      return result;
    }

    switch (loanAnonymizationConfigurationForTenant.getFeesAndFinesClosingType()) {
    case IMMEDIATELY:
      result.add(new AnonymizeLoansWithFeeFinesImmediatelyChecker());
      break;
    case INTERVAL:
      result.add(new FeesAndFinesClosePeriodChecker(
          loanAnonymizationConfigurationForTenant.getFeeFineClosePeriod()));
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