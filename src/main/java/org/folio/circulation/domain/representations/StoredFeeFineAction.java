package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.UUID;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public class StoredFeeFineAction extends JsonObject {
  public StoredFeeFineAction(StoredFeeFineActionBuilder builder) {
    super();

    write(this, "id", builder.id);
    write(this, "userId", builder.userId);
    write(this, "accountId", builder.accountId);
    write(this, "source", builder.createdBy);
    write(this, "createdAt", builder.createdAt);
    write(this, "transactionInformation", "-");
    write(this, "balance", builder.balance.toDouble());
    write(this, "amountAction", builder.amount.toDouble());
    write(this, "notify", false);
    write(this, "typeAction", builder.action);
    write(this, "dateAction", ClockUtil.getZonedDateTime());
  }

  public static StoredFeeFineActionBuilder builder() {
    return new StoredFeeFineActionBuilder();
  }

  public static StoredFeeFineActionBuilder builder(Account account) {
    return new StoredFeeFineActionBuilder().useAccount(account);
  }

  public static class StoredFeeFineActionBuilder {
    private String id = UUID.randomUUID().toString();
    private String userId;
    private String accountId;
    private String createdBy;
    private String createdAt;
    private FeeAmount balance;
    private FeeAmount amount;
    private String action;

    public StoredFeeFineActionBuilder useAccount(Account account) {
      return withUserId(account.getUserId())
        .withBalance(account.getRemaining())
        .withAmount(account.getAmount())
        .withAction(account.getFeeFineType())
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
      return withCreatedBy(user.getPersonalName());
    }

    public StoredFeeFineActionBuilder createdByAutomatedProcess() {
      return withCreatedBy("System");
    }

    public StoredFeeFineActionBuilder withCreatedAt(String createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public StoredFeeFineActionBuilder withCreatedAt(ServicePoint servicePoint) {
      return withCreatedAt(servicePoint != null ? servicePoint.getId() : null);
    }

    public StoredFeeFineActionBuilder withBalance(FeeAmount balance) {
      this.balance = balance;
      return this;
    }

    public StoredFeeFineActionBuilder withAmount(FeeAmount amount) {
      this.amount = amount;
      return this;
    }

    public StoredFeeFineActionBuilder withAction(String action) {
      this.action = action;
      return this;
    }

    public StoredFeeFineAction build() {
      return new StoredFeeFineAction(this);
    }
  }
}
