package org.folio.circulation.domain.anonymization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.folio.circulation.domain.Loan;

public class LoanAnonymizationRecords {

  private List<String> anonymizedLoans = new ArrayList<>();
  private List<Loan> loansFound = new ArrayList<>();
  private MultiValuedMap<String, String> notAnonymizedLoans =
      new HashSetValuedHashMap<>();

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
    newRecords.notAnonymizedLoans = new HashSetValuedHashMap<>(notAnonymizedLoans);
    return newRecords;
  }

  public LoanAnonymizationRecords withAnonymizedLoans(Collection<String> loans) {
    if (CollectionUtils.isEmpty(loans)) {
      return this;
    }
    LoanAnonymizationRecords newRecords = new LoanAnonymizationRecords();
    newRecords.loansFound = new ArrayList<>(loansFound);
    newRecords.anonymizedLoans = new ArrayList<>(loans);
    newRecords.notAnonymizedLoans = new HashSetValuedHashMap<>(notAnonymizedLoans);
    return newRecords;
  }

  public LoanAnonymizationRecords withNotAnonymizedLoans(
      MultiValuedMap<String, String> loans) {
    LoanAnonymizationRecords newRecords = new LoanAnonymizationRecords();
    newRecords.loansFound = new ArrayList<>(loansFound);
    newRecords.anonymizedLoans = new ArrayList<>(anonymizedLoans);
    newRecords.notAnonymizedLoans = new HashSetValuedHashMap<>(loans);
    return newRecords;
  }

  public List<String> getAnonymizedLoans() {
    return anonymizedLoans;
  }

  public MultiValuedMap<String, String> getNotAnonymizedLoans() {
    return notAnonymizedLoans;
  }
}
