package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.BigDecimalUtil.toDouble;

import java.math.BigDecimal;
import java.util.UUID;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.ClockManager;

import io.vertx.core.json.JsonObject;

public class FeeFineActionStorageRepresentation extends JsonObject {
  public FeeFineActionStorageRepresentation(Builder builder) {
    super();

    this.put("id", builder.id);
    this.put("userId", builder.userId);
    this.put("accountId", builder.accountId);
    this.put("source", builder.createdBy);
    this.put("createdAt", builder.createdAt);
    this.put("transactionInformation", builder.transactionInformation);
    this.put("balance", toDouble(builder.balance));
    this.put("amountAction", toDouble(builder.amount));
    this.put("notify", builder.notify);
    this.put("typeAction", builder.action);
    write(this, "dateAction", ClockManager.getClockManager().getDateTime());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String id = UUID.randomUUID().toString();
    private String userId;
    private String accountId;
    private String createdBy;
    private String createdAt;
    private String transactionInformation = "-";
    private BigDecimal balance;
    private BigDecimal amount;
    private boolean notify = false;
    private String action;

    public Builder useAccount(Account account) {
      return withUserId(account.getUserId())
        .withBalance(account.getRemaining())
        .withAmount(account.getAmount())
        .withAccountId(account.getId());
    }

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withUserId(String userId) {
      this.userId = userId;
      return this;
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder withCreatedBy(String createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withCreatedBy(User user) {
      return withCreatedBy(
        String.format("%s, %s", user.getLastName(), user.getFirstName()));
    }

    public Builder withCreatedAt(String createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withBalance(BigDecimal balance) {
      this.balance = balance;
      return this;
    }

    public Builder withAmount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder withAction(FeeFinePaymentAction action) {
      this.action = action.getValue();
      return this;
    }

    public Builder withAction(String action) {
      this.action = action;
      return this;
    }

    public FeeFineActionStorageRepresentation build() {
      return new FeeFineActionStorageRepresentation(this);
    }
  }
}
