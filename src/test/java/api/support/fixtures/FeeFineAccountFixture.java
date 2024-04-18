package api.support.fixtures;

import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.ResourceClient.forAccounts;
import static api.support.http.ResourceClient.forFeeFineActions;
import static java.lang.String.format;

import java.util.Collection;
import java.util.UUID;

import api.support.http.CqlQuery;
import api.support.http.IndividualResource;

import api.support.builders.AccountBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public final class FeeFineAccountFixture {
  private final ResourceClient accountsClient = forAccounts();
  private final ResourceClient accountActionsClient = forFeeFineActions();
  private final String LOST_ITEM_FEE_TYPE = "Lost item fee";
  private final String LOST_ITEM_PROCESSING_FEE_TYPE = "Lost item processing fee";
  private static final String LOST_ITEM_ACTUAL_COST_FEE_TYPE = "Lost item fee (actual cost)";

  public void transferLostItemFee(UUID loanId) {
    final JsonObject lostItemFeeAccount = getAccount(loanId, LOST_ITEM_FEE_TYPE);
    final String accountId = lostItemFeeAccount.getString("id");

    transfer(accountId, lostItemFeeAccount.getDouble("amount"));
  }

  public void transferLostItemFee(UUID loanId, double amount) {
    final String accountId = getAccount(loanId, LOST_ITEM_FEE_TYPE)
      .getString("id");

    transfer(accountId, amount);
  }

  public void transfer(String accountId, double amount) {
    final UUID accountUuid = UUID.fromString(accountId);
    final JsonObject account = accountsClient.getById(accountUuid).getJson();
    final double remaining = account.getDouble("remaining") - amount;
    final String actionType = remaining == 0 ? "Transferred fully" : "Transferred partially";
    final String accountStatus = remaining == 0 ? "Closed" : "Open";

    accountActionsClient.create(new FeefineActionsBuilder()
      .withAccountId(accountUuid)
      .withBalance(remaining)
      .withActionAmount(amount)
      .withPaymentMethod("Bursar")
      .withActionType(actionType)
      .withCreatedAt(UUID.randomUUID().toString()));

    accountsClient.replace(accountUuid, account.copy()
      .put("remaining", remaining)
      .put("status", new JsonObject().put("name", accountStatus))
      .put("paymentStatus", new JsonObject().put("name", actionType)));
  }

  public void payLostItemFee(UUID loanId) {
    final JsonObject lostItemFeeAccount = getAccount(
      loanId, LOST_ITEM_FEE_TYPE);

    final String accountId = lostItemFeeAccount.getString("id");
    pay(accountId, lostItemFeeAccount.getDouble("amount"));
  }

  public void payLostItemFee(UUID loanId, double amount) {
    final String accountId = getAccount(loanId, LOST_ITEM_FEE_TYPE)
      .getString("id");

    pay(accountId, amount);
  }

  public void payLostItemActualCostFee(UUID loanId, double amount) {
    final String accountId = getAccount(
      loanId, LOST_ITEM_ACTUAL_COST_FEE_TYPE).getString("id");

    pay(accountId, amount);
  }

  public void payLostItemActualCostFee(UUID loanId) {
    final JsonObject lostItemFeeActualCostAccount = getAccount(loanId, LOST_ITEM_ACTUAL_COST_FEE_TYPE);
    final String accountId = lostItemFeeActualCostAccount.getString("id");

    pay(accountId, lostItemFeeActualCostAccount.getDouble("amount"));
  }

  public void payLostItemProcessingFee(UUID loanId) {
    final JsonObject lostItemProcessingFeeAccount = getAccount(
      loanId, LOST_ITEM_PROCESSING_FEE_TYPE);

    final String accountId = lostItemProcessingFeeAccount.getString("id");
    pay(accountId, lostItemProcessingFeeAccount.getDouble("amount"));
  }

  public void payLostItemProcessingFee(UUID loanId, double amount) {
    final String accountId = getAccount(
      loanId, LOST_ITEM_PROCESSING_FEE_TYPE).getString("id");

    pay(accountId, amount);
  }

  public void pay(String accountId, double amount) {
    final UUID accountUuid = UUID.fromString(accountId);
    final JsonObject account = accountsClient.getById(accountUuid).getJson();
    final double remaining = account.getDouble("remaining") - amount;
    final String actionType = remaining == 0 ? "Paid fully" : "Paid partially";
    final String accountStatus = remaining == 0 ? "Closed" : "Open";

    accountActionsClient.create(new FeefineActionsBuilder()
      .withAccountId(accountUuid)
      .withBalance(remaining)
      .withActionAmount(amount)
      .withPaymentMethod("Cash")
      .withActionType(actionType)
      .withCreatedAt(UUID.randomUUID().toString()));

    accountsClient.replace(accountUuid, account.copy()
      .put("remaining", remaining)
      .put("status", new JsonObject().put("name", accountStatus))
      .put("paymentStatus", new JsonObject().put("name", actionType)));
  }

  public IndividualResource createManualFeeForLoan(IndividualResource loan, double amount) {
    final IndividualResource account = accountsClient.create(new AccountBuilder()
      .withLoan(loan)
      .withAmount(amount)
      .withRemainingFeeFine(amount)
      .feeFineStatusOpen()
      .manualFeeFine());

    accountActionsClient.create(new FeefineActionsBuilder()
      .withAccountId(account.getId())
      .withBalance(amount)
      .withActionAmount(amount)
      .withActionType("Manual fee fine")
      .withCreatedAt(UUID.randomUUID().toString()));

    return account;
  }

  public IndividualResource createLostItemFeeActualCostAccount(double amount,
    IndividualResource loan, IndividualResource feeFine, IndividualResource owner,
    String infoForStaff, String infoForPatron) {

    var account = accountsClient.create(new AccountBuilder()
      .withLoan(loan)
      .withAmount(amount)
      .withRemainingFeeFine(amount)
      .feeFineStatusOpen()
      .withFeeFineActualCostType()
      .withFeeFine(feeFine)
      .withOwner(owner)
      .withPaymentStatus("Outstanding"));

    accountActionsClient.create(new FeefineActionsBuilder()
      .withAccountId(account.getId())
      .withBalance(amount)
      .withActionAmount(amount)
      .withActionType("Lost item fee (actual cost)")
      .withComments(format("STAFF : %s \n PATRON : %s", infoForStaff, infoForPatron)));

    return account;
  }

  public Collection<JsonObject> getAccounts(UUID loanId) {
    return accountsClient.getMany(CqlQuery.exactMatch("loanId", loanId.toString()))
      .stream()
      .toList();
  }

  private JsonObject getLostItemFeeAccount(UUID loanId) {
    return accountsClient.getMany(exactMatch("loanId", loanId.toString())
        .and(exactMatch("feeFineType", "Lost item fee")))
      .getFirst();
  }

  private JsonObject getAccount(UUID loanId, String feeFineType) {
    return accountsClient.getMany(exactMatch("loanId", loanId.toString())
        .and(exactMatch("feeFineType", feeFineType)))
      .getFirst();
  }
}
