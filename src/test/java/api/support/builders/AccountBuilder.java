package api.support.builders;


import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;

import java.util.UUID;

import static org.folio.circulation.support.JsonPropertyWriter.write;

public class AccountBuilder extends JsonBuilder implements Builder {

  private String id;
  private String userId;
  private String loanId;
  private Double amount;
  private String status;

  public AccountBuilder() {
  }

  AccountBuilder(String loanId, Double amount, String status) {
    this.loanId = loanId;
    this.amount = amount;
    this.status = status;
    this.id = UUID.randomUUID().toString();

  }

  @Override
  public JsonObject create() {
    JsonObject accountRequest = new JsonObject();

    write(accountRequest, "id", id);
    write(accountRequest, "loanId", loanId);
    write(accountRequest, "userId", userId);
    write(accountRequest, "remaining", amount);

    JsonObject statusObject = new JsonObject();
    write(statusObject, "name", status);
    write(accountRequest, "status", statusObject);

    return accountRequest;
  }

  public AccountBuilder withLoan(IndividualResource loan) {
    return new AccountBuilder(loan.getId().toString(), amount, status);
  }

  public AccountBuilder withRemainingFeeFine(double remaining) {
    return new AccountBuilder(loanId, remaining, status);
  }

  public AccountBuilder feeFineStatusOpen() {
    return new AccountBuilder(loanId, amount, "Open");
  }

  public AccountBuilder feeFineStatusClosed() {
    return new AccountBuilder(loanId, amount, "Closed");
  }


}
