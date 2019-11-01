package org.folio.circulation.domain.anonymization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Set;
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

  public List<String> getAnonymizedLoans() {
    return anonymizedLoans;
  }

  public Map<String, Collection<String>> getNotAnonymizedLoans() {
    return notAnonymizedLoans;
  }
}
