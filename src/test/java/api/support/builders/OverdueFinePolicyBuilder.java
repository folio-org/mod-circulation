package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

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

  private OverdueFinePolicyBuilder(
    UUID id,
    String name,
    String description,
    JsonObject overdueFine,
    Boolean countClosed,
    double maxOverdueFine,
    boolean forgiveOverdueFine,
    JsonObject overdueRecallFine,
    Boolean gracePeriodRecall,
    double maxOverdueRecallFine) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.overdueFine = overdueFine;
    this.countClosed = countClosed;
    this.maxOverdueFine = maxOverdueFine;
    this.forgiveOverdueFine = forgiveOverdueFine;
    this.overdueRecallFine = overdueRecallFine;
    this.gracePeriodRecall = gracePeriodRecall;
    this.maxOverdueRecallFine = maxOverdueRecallFine;
  }

  public OverdueFinePolicyBuilder withName(String name) {
    return new OverdueFinePolicyBuilder(
      this.id,
      name,
      this.description,
      this.overdueFine,
      this.countClosed,
      this.maxOverdueFine,
      this.forgiveOverdueFine,
      this.overdueRecallFine,
      this.gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withId(UUID id) {
    return new OverdueFinePolicyBuilder(
      id,
      this.name,
      this.description,
      this.overdueFine,
      this.countClosed,
      this.maxOverdueFine,
      this.forgiveOverdueFine,
      this.overdueRecallFine,
      this.gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withDescription(String description) {
    return new OverdueFinePolicyBuilder(
      this.id,
      this.name,
      description,
      this.overdueFine,
      this.countClosed,
      this.maxOverdueFine,
      this.forgiveOverdueFine,
      this.overdueRecallFine,
      this.gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withOverdueFine(JsonObject overdueFine) {
    return new OverdueFinePolicyBuilder(
      this.id,
      this.name,
      this.description,
      overdueFine,
      this.countClosed,
      this.maxOverdueFine,
      this.forgiveOverdueFine,
      this.overdueRecallFine,
      this.gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withCountClosed(Boolean countClosed) {
    return new OverdueFinePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.overdueFine,
      countClosed,
      this.maxOverdueFine,
      this.forgiveOverdueFine,
      this.overdueRecallFine,
      this.gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withMaxOverdueFine(double maxOverdueFine) {
    return new OverdueFinePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.overdueFine,
      this.countClosed,
      maxOverdueFine,
      this.forgiveOverdueFine,
      this.overdueRecallFine,
      this.gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withForgiveOverdueFine(boolean forgiveOverdueFine) {
    return new OverdueFinePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.overdueFine,
      this.countClosed,
      this.maxOverdueFine,
      forgiveOverdueFine,
      this.overdueRecallFine,
      this.gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withOverdueRecallFine(JsonObject overdueRecallFine) {
    return new OverdueFinePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.overdueFine,
      this.countClosed,
      this.maxOverdueFine,
      this.forgiveOverdueFine,
      overdueRecallFine,
      this.gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withGracePeriodRecall(Boolean gracePeriodRecall) {
    return new OverdueFinePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.overdueFine,
      this.countClosed,
      this.maxOverdueFine,
      this.forgiveOverdueFine,
      this.overdueRecallFine,
      gracePeriodRecall,
      this.maxOverdueRecallFine
    );
  }

  public OverdueFinePolicyBuilder withMaxOverdueRecallFine(double maxOverdueRecallFine) {
    return new OverdueFinePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.overdueFine,
      this.countClosed,
      this.maxOverdueFine,
      this.forgiveOverdueFine,
      this.overdueRecallFine,
      this.gracePeriodRecall,
      maxOverdueRecallFine
    );
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
