package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static org.folio.circulation.domain.representations.LoanProperties.BORROWER;
import static org.folio.circulation.domain.representations.LoanProperties.LOAN_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.LOST_ITEM_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.OVERDUE_FINE_POLICY;
import static org.folio.circulation.domain.representations.LoanProperties.PATRON_GROUP_ID_AT_CHECKOUT;
import static org.folio.circulation.domain.representations.LoanProperties.REMINDERS;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.policy.Policy;
import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.resources.context.RenewalContext;

import io.vertx.core.json.JsonObject;

public class LoanRepresentation {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject extendedLoan(Loan loan) {
    if (loan == null) {
      log.warn("extendedLoan:: loan is null");
      return null;
    }

    JsonObject extendedRepresentation = extendedLoan(loan.asJson(), loan.getItem());

    if(loan.isDueDateChangedByNearExpireUser()) {
      log.info("extendedLoan:: due date changed by near expire user");
      extendedRepresentation.put("dueDateChangedByNearExpireUser", loan.isDueDateChangedByNearExpireUser());
    }

    if(loan.isDueDateChangedByHold()) {
      log.info("extendedLoan:: due date changed by hold");
      extendedRepresentation.put("dueDateChangedByHold",loan.isDueDateChangedByHold());
    }

    if(loan.getCheckinServicePoint() != null) {
      log.info("extendedLoan:: checkinServicePoint is not null");
      addAdditionalServicePointProperties(extendedRepresentation, loan.getCheckinServicePoint(), "checkinServicePoint");
    }

    if(loan.getCheckoutServicePoint() != null) {
      log.info("extendedLoan:: checkoutServicePoint is not null");
      addAdditionalServicePointProperties(extendedRepresentation, loan.getCheckoutServicePoint(), "checkoutServicePoint");
    }

    if (loan.getUser() != null) {
      log.info("extendedLoan:: user is not null");
      additionalBorrowerProperties(extendedRepresentation, loan.getUser());
    } else {
      //When there is no user, it means that the loan has been anonymized
      log.info("extendedLoan:: there is no user, removing borrower");
      extendedRepresentation.remove(BORROWER);
    }

    if (loan.getOverdueFinePolicy().isReminderFeesPolicy()
      && loan.getLastReminderFeeBilledNumber() != null) {
      extendedRepresentation.getJsonObject(REMINDERS)
        .put("renewalBlocked",
          !loan.getOverdueFinePolicy()
            .getRemindersPolicy().getAllowRenewalOfItemsWithReminderFees());
    }


    addPolicy(extendedRepresentation, loan.getLoanPolicy(), LOAN_POLICY);
    addPolicy(extendedRepresentation, loan.getOverdueFinePolicy(), OVERDUE_FINE_POLICY);
    addPolicy(extendedRepresentation, loan.getLostItemPolicy(), LOST_ITEM_POLICY);
    additionalAccountProperties(extendedRepresentation, loan);

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

  public JsonObject extendedLoan(RenewalContext renewalContext) {
    return extendedLoan(renewalContext.getLoan());
  }

  private JsonObject extendedLoan(JsonObject loan, Item item) {
    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    if (item != null && item.isFound()) {
      loan.put("item", new ItemSummaryRepresentation()
        .createItemSummary(item));
    }

    loan.remove(LoanProperties.AGED_TO_LOST_DELAYED_BILLING);

    return loan;
  }

  private void additionalAccountProperties(JsonObject loanRepresentation, Loan loan) {
    log.debug("additionalAccountProperties:: parameters loanRepresentation: {}, loan: {}",
      () -> loanRepresentation, () -> loan);
    if (loan.getAccounts() == null) {
      log.info("additionalAccountProperties:: accounts is null");
      return;
    }

    JsonObject feesAndFinesSummary = loanRepresentation.containsKey(LoanProperties.FEESANDFINES)
      ? loanRepresentation.getJsonObject(LoanProperties.FEESANDFINES)
      : new JsonObject();

    write(feesAndFinesSummary, "amountRemainingToPay", loan.getRemainingFeeFineAmount()
      .toDouble());
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

    if (servicePoint == null) {
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
    spSummary.put("pickupLocation", servicePoint.isPickupLocation());

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
    borrowerSummary.put("preferredFirstName",borrower.getPreferredFirstName());
    borrowerSummary.put("patronGroup",borrower.getPatronGroupId());
    loanRepresentation.put(BORROWER, borrowerSummary);
    additionalPatronGroupProperties(loanRepresentation, borrower.getPatronGroup());
  }

  private void additionalPatronGroupProperties(JsonObject loanRepresentation,
    PatronGroup patronGroupAtCheckout) {

    if (isNull(patronGroupAtCheckout)) {
      log.info("additionalPatronGroupProperties:: patronGroupAtCheckout is null");
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
