package org.folio.circulation.domain;

import static java.util.Objects.isNull;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.domain.representations.LoanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import static org.folio.circulation.support.JsonPropertyWriter.write;

public class LoanRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject extendedLoan(Loan loan) {
    if(loan == null) {
      return null;
    }

    JsonObject extendedRepresentation = extendedLoan(loan.asJson(), loan.getItem());
    
    if(loan.getCheckinServicePoint() != null) {
      addAdditionalServicePointProperties(extendedRepresentation, loan.getCheckinServicePoint(), "checkinServicePoint");
    }
    
    if(loan.getCheckoutServicePoint() != null) {
      addAdditionalServicePointProperties(extendedRepresentation, loan.getCheckoutServicePoint(), "checkoutServicePoint");
    }

    if (loan.getUser() != null) {
      additionalBorrowerProperties(extendedRepresentation, loan.getUser());
    }else{
      //When there is no user, it means that the loan has been anonymized
      extendedRepresentation.remove(LoanProperties.BORROWER);
    }

    if (loan.getLoanPolicy() != null) {
      additionalLoanPolicyProperties(extendedRepresentation, loan.getLoanPolicy());
    } else {
      extendedRepresentation.remove(LoanProperties.LOAN_POLICY);
    }

    additionalAccountProperties(extendedRepresentation, loan.getAccounts());

    extendedRepresentation.remove(LoanProperties.PATRON_GROUP_ID_AT_CHECKOUT);

    return extendedRepresentation;
  }


  public JsonObject extendedLoan(LoanAndRelatedRecords relatedRecords) {
    return extendedLoan(relatedRecords.getLoan());
  }

  private JsonObject extendedLoan(JsonObject loan, Item item) {
    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    if(item != null && item.isFound()) {
      loan.put("item", new ItemSummaryRepresentation()
        .createItemSummary(item));
    }

    return loan;
  }

  private void additionalAccountProperties(JsonObject loanRepresentation, Collection<Account> accounts) {
    if (accounts == null) {
      return;
    }
    double remainingFeesFines = accounts.stream().
      map(Account::getRemainingFeeFineAmount).reduce(Double::sum).orElse(0d);

    JsonObject feesAndFinesSummary = loanRepresentation.containsKey(LoanProperties.FEESANDFINES)
      ? loanRepresentation.getJsonObject(LoanProperties.FEESANDFINES)
      : new JsonObject();
    write(feesAndFinesSummary, "amountRemainingToPay", remainingFeesFines);
    write(loanRepresentation, LoanProperties.FEESANDFINES, feesAndFinesSummary);
  }

  private void additionalLoanPolicyProperties(JsonObject loanRepresentation, LoanPolicy loanPolicy) {
    if (loanPolicy == null) {
      log.info("Unable to add loan policy properties to loan {}," + " loanPolicy is null", loanRepresentation.getString("id"));
      return;
    }
    JsonObject loanPolicySummary = loanRepresentation.containsKey(LoanProperties.LOAN_POLICY)
        ? loanRepresentation.getJsonObject(LoanProperties.LOAN_POLICY)
        : new JsonObject();

    loanPolicySummary.put("name", loanPolicy.getName());

    loanRepresentation.put(LoanProperties.LOAN_POLICY, loanPolicySummary);
  }
  
  private void addAdditionalServicePointProperties(
    JsonObject loanRepresentation,
    ServicePoint servicePoint,
    String fieldName) {

    if(servicePoint == null) {
      log.info("Unable to add servicepoint properties to loan {},"
          + " servicepoint is null", loanRepresentation.getString("id"));
      return;
    }

    JsonObject spSummary = loanRepresentation.containsKey(fieldName)
        ? loanRepresentation.getJsonObject(fieldName)
        : new JsonObject();
    spSummary.put("name", servicePoint.getName());
    spSummary.put("code", servicePoint.getCode());
    spSummary.put("discoveryDisplayName", servicePoint.getDiscoveryDisplayName());
    spSummary.put("description", servicePoint.getDescription());
    spSummary.put("shelvingLagTime", servicePoint.getShelvingLagTime());
    spSummary.put("pickupLocation", servicePoint.getPickupLocation());

    loanRepresentation.put(fieldName, spSummary);
  }

  private void additionalBorrowerProperties(JsonObject loanRepresentation, User borrower) {
    if (borrower == null) {
      log.info("Unable to add borrower properties to loan {},"
        + " borrower is null", loanRepresentation.getString("id"));
      return;
    }

    JsonObject borrowerSummary = loanRepresentation.containsKey(LoanProperties.BORROWER)
        ? loanRepresentation.getJsonObject(LoanProperties.BORROWER)
        : new JsonObject();
    borrowerSummary.put("firstName", borrower.getFirstName());
    borrowerSummary.put("lastName", borrower.getLastName());
    borrowerSummary.put("middleName", borrower.getMiddleName());
    borrowerSummary.put("barcode", borrower.getBarcode());

    loanRepresentation.put(LoanProperties.BORROWER, borrowerSummary);

    additionalPatronGroupProperties(loanRepresentation, borrower.getPatronGroup());
  }

  private void additionalPatronGroupProperties(JsonObject loanRepresentation, PatronGroup patronGroupAtCheckout) {
    if (isNull(patronGroupAtCheckout)) {
      log.info("Unable to add patron group at checkout properties to loan {},"
        + " patron group is null", loanRepresentation.getString("id"));
      return;
    }

    JsonObject patronGroupAtCheckoutSummary = loanRepresentation.containsKey(LoanProperties.PATRON_GROUP_AT_CHECKOUT)
      ? loanRepresentation.getJsonObject(LoanProperties.PATRON_GROUP_AT_CHECKOUT)
      : new JsonObject();
    patronGroupAtCheckoutSummary.put("id", patronGroupAtCheckout.getId());
    patronGroupAtCheckoutSummary.put("name", patronGroupAtCheckout.getGroup());

    loanRepresentation.put(LoanProperties.PATRON_GROUP_AT_CHECKOUT, patronGroupAtCheckoutSummary);
  }
}
