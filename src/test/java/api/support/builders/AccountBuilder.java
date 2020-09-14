package api.support.builders;


import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import io.vertx.core.json.JsonObject;

public class AccountBuilder extends JsonBuilder implements Builder {

  private String id;
  private String loanId;
  private Double remainingAmount;
  private Double amount;
  private String status;
  private String feeFineType;

  public AccountBuilder() {
  }

  AccountBuilder(String loanId, Double amount, Double remainingAmount,
    String status, String feeFineType) {

    this.loanId = loanId;
    this.amount = amount;
    this.remainingAmount = remainingAmount;
    this.status = status;
    this.id = UUID.randomUUID().toString();
    this.feeFineType = feeFineType;
  }

  @Override
  public JsonObject create() {
    JsonObject accountRequest = new JsonObject();

    write(accountRequest, "id", id);
    write(accountRequest, "loanId", loanId);
    write(accountRequest, "amount", amount);
    write(accountRequest, "remaining", remainingAmount);
    write(accountRequest, "feeFineType", feeFineType);

    JsonObject statusObject = new JsonObject();
    write(statusObject, "name", status);
    write(accountRequest, "status", statusObject);

    return accountRequest;
  }

  public AccountBuilder withLoan(IndividualResource loan) {
    return new AccountBuilder(loan.getId().toString(), amount, remainingAmount,
      status, feeFineType);
  }

  public AccountBuilder withRemainingFeeFine(double remaining) {
    return new AccountBuilder(loanId, amount, remaining, status, feeFineType);
  }

  public AccountBuilder withAmount(double amount) {
    return new AccountBuilder(loanId, amount, remainingAmount, status, feeFineType);
  }

  public AccountBuilder feeFineStatusOpen() {
    return new AccountBuilder(loanId, amount, remainingAmount, "Open", feeFineType);
  }

  public AccountBuilder feeFineStatusClosed() {
    return new AccountBuilder(loanId, amount, remainingAmount, "Closed", feeFineType);
  }

  public AccountBuilder manualFeeFine() {
    return new AccountBuilder(loanId, amount, remainingAmount, status, "Manual fee fine");
  }
}
