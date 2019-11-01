package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class FeefineActionsBuilder extends JsonBuilder implements Builder {

  private String id;
  private DateTime dateAction;
  private Double balance;
  private UUID accountId;

  public FeefineActionsBuilder() {
  }

  public FeefineActionsBuilder(DateTime dateAction, Double balance,
      UUID accountId) {
    this.dateAction = dateAction;
    this.balance = balance;
    this.accountId = accountId;
    this.id = UUID.randomUUID().toString();
  }

  @Override
  public JsonObject create() {
    JsonObject object = new JsonObject();

    write(object, "id", id);
    write(object, "dateAction", dateAction);
    write(object, "balance", balance);
    write(object, "accountId", accountId);

    return object;
  }

  public FeefineActionsBuilder withDate(DateTime dateAction) {
    return new FeefineActionsBuilder(dateAction, balance,accountId);
  }

  public FeefineActionsBuilder withBalance(double remaining) {
    return new FeefineActionsBuilder(dateAction, remaining,accountId);
  }

  public FeefineActionsBuilder forAccount(UUID accountId) {
    return new FeefineActionsBuilder(dateAction, balance,accountId);
  }

}
