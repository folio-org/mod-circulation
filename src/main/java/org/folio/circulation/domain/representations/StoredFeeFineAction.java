package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.ClockManager;

import io.vertx.core.json.JsonObject;

public class StoredFeeFineAction extends JsonObject {
  public StoredFeeFineAction(StoredFeeFineActionBuilder builder) {
    super();

    this.put("id", builder.id);
    this.put("userId", builder.userId);
    this.put("accountId", builder.accountId);
    this.put("source", builder.createdBy);
    this.put("createdAt", builder.createdAt);
    this.put("transactionInformation", builder.transactionInformation);
    this.put("balance", builder.balance.toDouble());
    this.put("amountAction", builder.amount.toDouble());
    this.put("notify", builder.notify);
    this.put("typeAction", builder.action);

    write(this, "paymentMethod", builder.paymentMethod);
    write(this, "dateAction", ClockManager.getClockManager().getDateTime());
  }

  public static StoredFeeFineActionBuilder builder() {
    return new StoredFeeFineActionBuilder();
  }

  public static class StoredFeeFineActionBuilder {
    private String id = UUID.randomUUID().toString();
    private String userId;
    private String accountId;
    private String createdBy;
    private String createdAt;
    private String transactionInformation = "-";
    private FeeAmount balance;
    private FeeAmount amount;
    private boolean notify = false;
    private String action;
    private String paymentMethod;

    public StoredFeeFineActionBuilder useAccount(Account account) {
      return withUserId(account.getUserId())
        .withBalance(account.getRemaining())
        .withAmount(account.getAmount())
        .withAccountId(account.getId());
    }

    public StoredFeeFineActionBuilder withId(String id) {
      this.id = id;
      return this;
    }

    public StoredFeeFineActionBuilder withUserId(String userId) {
      this.userId = userId;
      return this;
    }

    public StoredFeeFineActionBuilder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public StoredFeeFineActionBuilder withCreatedBy(String createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public StoredFeeFineActionBuilder withCreatedBy(User user) {
      return withCreatedBy(
        String.format("%s, %s", user.getLastName(), user.getFirstName()));
    }

    public StoredFeeFineActionBuilder withCreatedAt(String createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public StoredFeeFineActionBuilder withBalance(FeeAmount balance) {
      this.balance = balance;
      return this;
    }

    public StoredFeeFineActionBuilder withAmount(FeeAmount amount) {
      this.amount = amount;
      return this;
    }

    public StoredFeeFineActionBuilder withAction(AccountPaymentStatus action) {
      this.action = action.getValue();
      return this;
    }

    public StoredFeeFineActionBuilder withAction(String action) {
      this.action = action;
      return this;
    }

    public StoredFeeFineActionBuilder withTransactionInformation(String transactionInfo) {
      this.transactionInformation = transactionInfo;
      return this;
    }

    public StoredFeeFineAction build() {
      return new StoredFeeFineAction(this);
    }

    public StoredFeeFineActionBuilder withPaymentMethod(String paymentMethod) {
      this.paymentMethod = paymentMethod;
      return this;
    }
  }
}
