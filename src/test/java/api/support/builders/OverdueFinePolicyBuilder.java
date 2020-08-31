package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
public class OverdueFinePolicyBuilder extends JsonBuilder implements Builder  {

  private final UUID id;
  private final String name;
  private final String description;
  private final JsonObject overdueFine;
  private final Boolean countClosed;
  private final double maxOverdueFine;
  private final boolean forgiveOverdueFine;
  private final JsonObject overdueRecallFine;
  private final Boolean gracePeriodRecall;
  private final double maxOverdueRecallFine;

  public OverdueFinePolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Overdue Fine Policy",
      "An example overdue fine policy",
      new JsonObject(),
      true,
      10.00,
      true,
      new JsonObject(),
      false,
      10.00
    );
  }

  public OverdueFinePolicyBuilder withOverdueFine(double quantity, String intervalId) {
    final JsonObject overdueFine = new JsonObject()
      .put("quantity", quantity)
      .put("intervalId", intervalId);

    return withOverdueFine(overdueFine);
  }

  public OverdueFinePolicyBuilder withOverdueRecallFine(double quantity, String intervalId) {
    final JsonObject overdueFine = new JsonObject()
      .put("quantity", quantity)
      .put("intervalId", intervalId);

    return withOverdueRecallFine(overdueFine);
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if (id != null) {
      put(request, "id", id.toString());
    }

    put(request, "name", this.name);
    put(request, "description", this.description);
    put(request, "overdueFine", this.overdueFine);
    put(request, "countClosed", this.countClosed);
    put(request, "maxOverdueFine", this.maxOverdueFine);
    put(request, "forgiveOverdueFine", this.forgiveOverdueFine);
    put(request, "overdueRecallFine", this.overdueRecallFine);
    put(request, "gracePeriodRecall", this.gracePeriodRecall);
    put(request, "maxOverdueRecallFine", this.maxOverdueRecallFine);
    return request;
  }
}
