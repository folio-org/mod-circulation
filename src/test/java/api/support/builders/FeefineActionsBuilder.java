package api.support.builders;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class FeefineActionsBuilder extends JsonBuilder implements Builder {

  private String id;
  private DateTime dateAction = ClockUtil.getDateTime();
  private Double balance;
  private Double actionAmount;
  private String paymentMethod;
  private String actionType;
  private String createdAt;
  private UUID accountId;

  public FeefineActionsBuilder() {
  }

  public FeefineActionsBuilder(DateTime dateAction, Double balance,
    UUID accountId, Double actionAmount, String paymentMethod, String actionType,
    String createdAt) {

    this.dateAction = dateAction;
    this.balance = balance;
    this.accountId = accountId;
    this.id = UUID.randomUUID().toString();
    this.actionAmount = actionAmount;
    this.paymentMethod = paymentMethod;
    this.actionType = actionType;
    this.createdAt = createdAt;
  }

  @Override
  public JsonObject create() {
    JsonObject object = new JsonObject();

    write(object, "id", id);
    write(object, "dateAction", dateAction);
    write(object, "balance", balance);
    write(object, "amountAction", actionAmount);
    write(object, "paymentMethod", paymentMethod);
    write(object, "typeAction", actionType);
    write(object, "createdAt", createdAt);
    write(object, "source", "Admin, Admin");
    write(object, "accountId", accountId);

    return object;
  }

  public FeefineActionsBuilder withDate(DateTime dateAction) {
    return new FeefineActionsBuilder(dateAction, balance, accountId, actionAmount,
      paymentMethod, actionType, createdAt);
  }

  public FeefineActionsBuilder withBalance(double remaining) {
    return new FeefineActionsBuilder(dateAction, remaining, accountId, actionAmount,
      paymentMethod, actionType, createdAt);
  }

  public FeefineActionsBuilder withActionAmount(double amountAction) {
    return new FeefineActionsBuilder(dateAction, balance, accountId, amountAction,
      paymentMethod, actionType, createdAt);
  }

  public FeefineActionsBuilder withPaymentMethod(String paymentMethod) {
    return new FeefineActionsBuilder(dateAction, balance, accountId, actionAmount,
      paymentMethod, actionType, createdAt);
  }

  public FeefineActionsBuilder withActionType(String actionType) {
    return new FeefineActionsBuilder(dateAction, balance, accountId, actionAmount,
      paymentMethod, actionType, createdAt);
  }

  public FeefineActionsBuilder forAccount(UUID accountId) {
    return new FeefineActionsBuilder(dateAction, balance, accountId, actionAmount,
      paymentMethod, actionType, createdAt);
  }

  public FeefineActionsBuilder createdAt(String createdAt) {
    return new FeefineActionsBuilder(dateAction, balance, accountId, actionAmount,
      paymentMethod, actionType, createdAt);
  }
}
