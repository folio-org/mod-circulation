package org.folio.circulation.domain.anonymization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.circulation.domain.Loan;

public class LoanAnonymizationRecords {

  private String userId;
  private String tenant;
  private List<String> anonymizedLoans = new ArrayList<>();
  private List<Loan> inputLoans = new ArrayList<>();
  private List<String> notAnonymizedLoans = new ArrayList<>();

  public LoanAnonymizationRecords(String userId, String tenant) {
    this.userId = userId;
    this.tenant = tenant;
  }

  public List<Loan> getInputLoans() {
    return inputLoans;
  }

  public LoanAnonymizationRecords withInputLoans(Collection<Loan> loans) {
    if (CollectionUtils.isEmpty(loans)) {
      return this;
    }
    LoanAnonymizationRecords newRecords = new LoanAnonymizationRecords(userId, tenant);
    newRecords.inputLoans = new ArrayList<>(loans);
    newRecords.anonymizedLoans = new ArrayList<>(anonymizedLoans);
    newRecords.notAnonymizedLoans = new ArrayList<>(notAnonymizedLoans);
    return newRecords;
  }

  public LoanAnonymizationRecords withAnonymizedLoans(Collection<String> loans) {
    if (CollectionUtils.isEmpty(loans)) {
      return this;
    }
    LoanAnonymizationRecords newRecords = new LoanAnonymizationRecords(userId, tenant);
    newRecords.inputLoans = new ArrayList<>(inputLoans);
    newRecords.anonymizedLoans = new ArrayList<>(loans);
    newRecords.notAnonymizedLoans = new ArrayList<>(notAnonymizedLoans);
    return newRecords;
  }

  public LoanAnonymizationRecords withNotAnonymizedLoans(Collection<String> loans) {
    if (CollectionUtils.isEmpty(loans)) {
      return this;
    }
    LoanAnonymizationRecords newRecords = new LoanAnonymizationRecords(userId, tenant);
    newRecords.inputLoans = new ArrayList<>(inputLoans);
    newRecords.anonymizedLoans = new ArrayList<>(anonymizedLoans);
    newRecords.notAnonymizedLoans = new ArrayList<>(loans);
    return newRecords;
  }

  public String getUserId() {
    return userId;
  }

  public String getTenant() {
    return tenant;
  }

  public List<String> getAnonymizedLoans() {
    return anonymizedLoans;
  }

  public List<String> getNotAnonymizedLoans() {
    return notAnonymizedLoans;
  }
}
