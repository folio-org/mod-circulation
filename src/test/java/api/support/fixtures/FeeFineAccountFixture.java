package api.support.fixtures;

import static api.support.http.CqlQuery.exactMatch;
import static api.support.http.ResourceClient.forAccounts;
import static api.support.http.ResourceClient.forFeeFineActions;

import java.util.UUID;

import api.support.builders.FeefineActionsBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public final class FeeFineAccountFixture {
  private final ResourceClient accountsClient = forAccounts();
  private final ResourceClient accountActionsClient = forFeeFineActions();

  public JsonObject getLostItemFeeAccount(UUID loanId) {
    return accountsClient.getMany(exactMatch("loanId", loanId.toString())
      .and(exactMatch("feeFineType", "Lost item fee")))
      .getFirst();
  }

  public JsonObject getLostItemProcessingFeeAccount(UUID loanId) {
    return accountsClient.getMany(exactMatch("loanId", loanId.toString())
      .and(exactMatch("feeFineType", "Lost item processing fee")))
      .getFirst();
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

    pay(lostItemFeeAccount, lostItemFeeAccount.getDouble("amount"));
  }

  public void payLostItemProcessingFee(UUID loanId) {
    final JsonObject lostItemProcessingFeeAccount = getLostItemProcessingFeeAccount(loanId);

    pay(lostItemProcessingFeeAccount, lostItemProcessingFeeAccount.getDouble("amount"));
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

  public void transfer(JsonObject accountJson, double amount) {
    transfer(accountJson.getString("id"), amount);
  }

  public void pay(JsonObject accountJson, double amount) {
    pay(accountJson.getString("id"), amount);
  }
}
