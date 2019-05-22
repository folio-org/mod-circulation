package api.support.fixtures;

import java.util.Arrays;
import java.util.List;

public class NoticeTokens {

  private NoticeTokens() {
  }

  public static final List<String> EXPECTED_USER_TOKENS = Arrays.asList(
    "user.firstName", "user.lastName", "user.barcode");

  public static final List<String> EXPECTED_ITEM_TOKENS = Arrays.asList(
    "item.title", "item.allContributors", "item.barcode", "item.callNumber",
    "item.materialType", "item.loanType");

  public static final List<String> EXPECTED_LOAN_TOKENS = Arrays.asList(
    "loan.initialBorrowDate", "loan.numberOfRenewalsTaken", "loan.dueDate");

  public static final List<String> EXPECTED_LOAN_POLICY_TOKENS = Arrays.asList(
    "loan.numberOfRenewalsAllowed", "loan.numberOfRenewalsRemaining");

  public static final List<String> EXPECTED_REQUEST_TOKENS = Arrays.asList(
    "request.servicePointPickup", "request.requestExpirationDate ", "request.holdShelfExpirationDate");

  public static final List<String> EXPECTED_TOKENS_FOR_CANCELLED_REQUEST = Arrays.asList(
    "request.additionalInfo", "request.cancellationReason");

}
