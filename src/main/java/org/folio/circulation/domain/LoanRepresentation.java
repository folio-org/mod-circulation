package org.folio.circulation.domain;

import static java.util.Objects.isNull;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.Policy;
import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.domain.representations.LoanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import static org.folio.circulation.domain.representations.LoanProperties.BORROWER;
import static org.folio.circulation.domain.representations.LoanProperties.LOAN_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.LOST_ITEM_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.OVERDUE_FINE_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.PATRON_GROUP_ID_AT_CHECKOUT;
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
      extendedRepresentation.remove(BORROWER);
    }

    addPolicy(extendedRepresentation, loan.getLoanPolicy(), LOAN_POLICY);
    addPolicy(extendedRepresentation, loan.getOverdueFinePolicy(), OVERDUE_FINE_POLICY);
    addPolicy(extendedRepresentation, loan.getLostItemPolicy(), LOST_ITEM_POLICY);
    additionalAccountProperties(extendedRepresentation, loan.getAccounts());

    extendedRepresentation.remove(PATRON_GROUP_ID_AT_CHECKOUT);

    return extendedRepresentation;
  }

  private void addPolicy(JsonObject extendedRepresentation, Policy policy,
    String policyName) {
    if (policy != null) {
      additionalPolicyProperties(extendedRepresentation, policy, policyName);
    } else {
      log.info("Unable to add {} properties to loan {}, {} is null",
        policyName, extendedRepresentation.getString("id"), policyName);
      extendedRepresentation.remove(policyName);
    }
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
    double remainingFeesFines = accounts.stream().filter(Account::isOpen)
      .map(Account::getRemainingFeeFineAmount).reduce(Double::sum).orElse(0d);

    JsonObject feesAndFinesSummary = loanRepresentation.containsKey(LoanProperties.FEESANDFINES)
      ? loanRepresentation.getJsonObject(LoanProperties.FEESANDFINES)
      : new JsonObject();
    write(feesAndFinesSummary, "amountRemainingToPay", remainingFeesFines);
    write(loanRepresentation, LoanProperties.FEESANDFINES, feesAndFinesSummary);
  }

  private void additionalPolicyProperties(JsonObject representation,
    Policy policy, String policyName) {
    JsonObject summary = representation.containsKey(policyName)
      ? representation.getJsonObject(policyName)
      : new JsonObject();

    summary.put("name", policy.getName());
    representation.put(policyName, summary);
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

    JsonObject borrowerSummary = loanRepresentation.containsKey(BORROWER)
        ? loanRepresentation.getJsonObject(BORROWER)
        : new JsonObject();
    borrowerSummary.put("firstName", borrower.getFirstName());
    borrowerSummary.put("lastName", borrower.getLastName());
    borrowerSummary.put("middleName", borrower.getMiddleName());
    borrowerSummary.put("barcode", borrower.getBarcode());

    loanRepresentation.put(BORROWER, borrowerSummary);

    additionalPatronGroupProperties(loanRepresentation, borrower.getPatronGroup());
  }

  private void additionalPatronGroupProperties(JsonObject loanRepresentation, PatronGroup patronGroupAtCheckout) {
    if (isNull(patronGroupAtCheckout)) {
      return;
    }

    JsonObject patronGroupAtCheckoutSummary = loanRepresentation.containsKey(LoanProperties.PATRON_GROUP_AT_CHECKOUT)
      ? loanRepresentation.getJsonObject(LoanProperties.PATRON_GROUP_AT_CHECKOUT)
      : new JsonObject();
    write(patronGroupAtCheckoutSummary, "id", patronGroupAtCheckout.getId());
    write(patronGroupAtCheckoutSummary, "name", patronGroupAtCheckout.getGroup());

    loanRepresentation.put(LoanProperties.PATRON_GROUP_AT_CHECKOUT, patronGroupAtCheckoutSummary);
  }
}
