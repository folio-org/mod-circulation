package api.support.fixtures;

import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.ResourceClient.forAccounts;
import static api.support.http.ResourceClient.forFeeFineActions;

import java.util.UUID;

import api.support.http.IndividualResource;

import api.support.builders.AccountBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public final class FeeFineAccountFixture {
  private final ResourceClient accountsClient = forAccounts();
  private final ResourceClient accountActionsClient = forFeeFineActions();

  public void transferLostItemFee(UUID loanId) {
    final JsonObject lostItemFeeAccount = getLostItemFeeAccount(loanId);
    final String accountId = lostItemFeeAccount.getString("id");

    transfer(accountId, lostItemFeeAccount.getDouble("amount"));
  }

  public void transferLostItemFee(UUID loanId, double amount) {
    final String accountId = getLostItemFeeAccount(loanId).getString("id");

    transfer(accountId, amount);
  }

  public void transfer(String accountId, double amount) {
    final UUID accountUuid = UUID.fromString(accountId);
    final JsonObject account = accountsClient.getById(accountUuid).getJson();
    final double remaining = account.getDouble("remaining") - amount;
    final String actionType = remaining == 0 ? "Transferred fully" : "Transferred partially";
    final String accountStatus = remaining == 0 ? "Closed" : "Open";

    accountActionsClient.create(new FeefineActionsBuilder()
      .forAccount(accountUuid)
      .withBalance(remaining)
      .withActionAmount(amount)
      .withPaymentMethod("Bursar")
      .withActionType(actionType)
      .createdAt("Circ Desk 1"));

    accountsClient.replace(accountUuid, account.copy()
      .put("remaining", remaining)
      .put("status", new JsonObject().put("name", accountStatus))
      .put("paymentStatus", new JsonObject().put("name", actionType)));
  }

  public void payLostItemFee(UUID loanId) {
    final JsonObject lostItemFeeAccount = getLostItemFeeAccount(loanId);
    final String accountId = lostItemFeeAccount.getString("id");

    pay(accountId, lostItemFeeAccount.getDouble("amount"));
  }

  public void payLostItemFee(UUID loanId, double amount) {
    final String accountId = getLostItemFeeAccount(loanId).getString("id");

    pay(accountId, amount);
  }

  public void payLostItemProcessingFee(UUID loanId) {
    final JsonObject lostItemProcessingFeeAccount = getLostItemProcessingFeeAccount(loanId);
    final String accountId = lostItemProcessingFeeAccount.getString("id");

    pay(accountId, lostItemProcessingFeeAccount.getDouble("amount"));
  }

  public void payLostItemProcessingFee(UUID loanId, double amount) {
    final String accountId = getLostItemProcessingFeeAccount(loanId).getString("id");

    pay(accountId, amount);
  }

  public void pay(String accountId, double amount) {
    final UUID accountUuid = UUID.fromString(accountId);
    final JsonObject account = accountsClient.getById(accountUuid).getJson();
    final double remaining = account.getDouble("remaining") - amount;
    final String actionType = remaining == 0 ? "Paid fully" : "Paid partially";
    final String accountStatus = remaining == 0 ? "Closed" : "Open";

    accountActionsClient.create(new FeefineActionsBuilder()
      .forAccount(accountUuid)
      .withBalance(remaining)
      .withActionAmount(amount)
      .withPaymentMethod("Cash")
      .withActionType(actionType)
      .createdAt("Circ Desk 1"));

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
      .forAccount(account.getId())
      .withBalance(amount)
      .withActionAmount(amount)
      .withActionType("Manual fee fine")
      .createdAt("Circ Desk 1"));

    return account;
  }

  private JsonObject getLostItemFeeAccount(UUID loanId) {
    return accountsClient.getMany(exactMatch("loanId", loanId.toString())
      .and(exactMatch("feeFineType", "Lost item fee")))
      .getFirst();
  }

  private JsonObject getLostItemProcessingFeeAccount(UUID loanId) {
    return accountsClient.getMany(exactMatch("loanId", loanId.toString())
      .and(exactMatch("feeFineType", "Lost item processing fee")))
      .getFirst();
  }
}
