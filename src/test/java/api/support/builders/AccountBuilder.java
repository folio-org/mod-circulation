package api.support.builders;


import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.UUID;

import api.support.http.IndividualResource;

import io.vertx.core.json.JsonObject;

public class AccountBuilder extends JsonBuilder implements Builder {

  private String id;
  private String loanId;
  private Double remainingAmount;
  private Double amount;
  private String status;
  private String feeFineType;
  private String feeFineId;
  private String ownerId;
  private String userId;

  public AccountBuilder() {
  }

  AccountBuilder(String loanId, Double amount, Double remainingAmount,
    String status, String feeFineType, String feeFineId, String ownerId, String userId) {

    this.loanId = loanId;
    this.amount = amount;
    this.remainingAmount = remainingAmount;
    this.status = status;
    this.id = UUID.randomUUID().toString();
    this.feeFineType = feeFineType;
    this.feeFineId = feeFineId;
    this.ownerId = ownerId;
    this.userId = userId;
  }

  @Override
  public JsonObject create() {
    JsonObject accountRequest = new JsonObject();

    write(accountRequest, "id", id);
    write(accountRequest, "loanId", loanId);
    write(accountRequest, "amount", amount);
    write(accountRequest, "remaining", remainingAmount);
    write(accountRequest, "feeFineType", feeFineType);
    write(accountRequest, "feeFineId", feeFineId);
    write(accountRequest, "ownerId", ownerId);
    write(accountRequest, "userId", userId);

    JsonObject statusObject = new JsonObject();
    write(statusObject, "name", status);
    write(accountRequest, "status", statusObject);

    JsonObject paymentStatusObject = new JsonObject();
    write(paymentStatusObject, "name", "Outstanding");
    write(accountRequest, "paymentStatus", paymentStatusObject);

    return accountRequest;
  }

  public AccountBuilder withLoan(IndividualResource loan) {
    return new AccountBuilder(loan.getId().toString(), amount, remainingAmount,
      status, feeFineType, feeFineId, ownerId, userId);
  }

  public AccountBuilder withRemainingFeeFine(double remaining) {
    return new AccountBuilder(loanId, amount, remaining, status, feeFineType,
      feeFineId, ownerId, userId);
  }

  public AccountBuilder withAmount(double amount) {
    return new AccountBuilder(loanId, amount, remainingAmount, status, feeFineType,
      feeFineId, ownerId, userId);
  }

  public AccountBuilder feeFineStatusOpen() {
    return new AccountBuilder(loanId, amount, remainingAmount, "Open", feeFineType,
      feeFineId, ownerId, userId);
  }

  public AccountBuilder feeFineStatusClosed() {
    return new AccountBuilder(loanId, amount, remainingAmount, "Closed", feeFineType,
      feeFineId, ownerId, userId);
  }

  public AccountBuilder manualFeeFine() {
    return new AccountBuilder(loanId, amount, remainingAmount, status, "Manual fee fine",
      feeFineId, ownerId, userId);
  }

  public AccountBuilder withFeeFineActualCostType() {
    return new AccountBuilder(loanId, amount, remainingAmount, status,
      "Lost item fee (actual cost)", feeFineId, ownerId, userId);
  }

  public AccountBuilder withFeeFine(IndividualResource feeFine) {
    return new AccountBuilder(loanId, amount, remainingAmount, status, feeFineType,
      feeFine.getId().toString(), ownerId, userId);
  }

  public AccountBuilder withOwner(IndividualResource owner) {
    return new AccountBuilder(loanId, amount, remainingAmount, status, feeFineType, feeFineId,
      owner.getId().toString(), userId);
  }

  public AccountBuilder withUser(IndividualResource user) {
    return new AccountBuilder(loanId, amount, remainingAmount, status, feeFineType,
      feeFineId, ownerId, user.getId().toString());
  }
}
