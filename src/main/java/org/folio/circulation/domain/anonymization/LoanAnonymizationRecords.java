package org.folio.circulation.domain.anonymization;

import static java.lang.String.format;
import static org.folio.circulation.support.utils.LogUtil.listAsString;
import static org.folio.circulation.support.utils.LogUtil.mapAsString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.circulation.domain.Loan;

public class LoanAnonymizationRecords {

  public static final String CAN_BE_ANONYMIZED_KEY = "_";

  private List<String> anonymizedLoans = new ArrayList<>();
  private List<Loan> loansFound = new ArrayList<>();
  private Map<String, Collection<String>> notAnonymizedLoans =
      new HashMap<>();

  public List<Loan> getLoansFound() {
    return loansFound;
  }

  public LoanAnonymizationRecords withLoansFound(Collection<Loan> loans) {
    if (CollectionUtils.isEmpty(loans)) {
      return this;
    }
    LoanAnonymizationRecords newRecords = new LoanAnonymizationRecords();
    newRecords.loansFound = new ArrayList<>(loans);
    newRecords.anonymizedLoans = new ArrayList<>(anonymizedLoans);
    newRecords.notAnonymizedLoans = new HashMap<>(notAnonymizedLoans);
    return newRecords;
  }

  public LoanAnonymizationRecords withAnonymizedLoans(Collection<String> loans) {
    if (CollectionUtils.isEmpty(loans)) {
      return this;
    }
    LoanAnonymizationRecords newRecords = new LoanAnonymizationRecords();
    newRecords.loansFound = new ArrayList<>(loansFound);
    newRecords.anonymizedLoans = new ArrayList<>(loans);
    newRecords.notAnonymizedLoans = new HashMap<>(notAnonymizedLoans);
    return newRecords;
  }

  public LoanAnonymizationRecords withNotAnonymizedLoans(
      Map<String, Set<String>> loans) {
    LoanAnonymizationRecords newRecords = new LoanAnonymizationRecords();
    newRecords.loansFound = new ArrayList<>(loansFound);
    newRecords.anonymizedLoans = new ArrayList<>(anonymizedLoans);
    newRecords.notAnonymizedLoans = new HashMap<>(loans);
    return newRecords;
  }

  public List<String> getAnonymizedLoanIds() {
    return anonymizedLoans;
  }

  public List<Loan> getAnonymizedLoans() {
    return loansFound.stream()
      .filter(loan -> anonymizedLoans.contains(loan.getId()))
      .collect(Collectors.toList());
  }

  public Map<String, Collection<String>> getNotAnonymizedLoans() {
    return notAnonymizedLoans;
  }

  @Override
  public String toString() {
    return format("LoanAnonymizationRecords(anonymizedLoans=%s, loansFound=%s, " +
      "notAnonymizedLoans=%s)", listAsString(anonymizedLoans), listAsString(loansFound),
      mapAsString(notAnonymizedLoans));
  }
}
