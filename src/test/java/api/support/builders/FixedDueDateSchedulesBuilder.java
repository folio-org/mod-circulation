package api.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class FixedDueDateSchedulesBuilder extends JsonBuilder implements Builder {

  private final UUID id;
  private final String name;
  private final String description;
  private final Set<FixedDueDateSchedule> schedules;

  public FixedDueDateSchedulesBuilder() {
    this(UUID.randomUUID(), null, null, new HashSet<>());
  }

  private FixedDueDateSchedulesBuilder(
    UUID id,
    String name,
    String description,
    Set<FixedDueDateSchedule> schedules) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.schedules = schedules;
  }

  @Override
  public JsonObject create() {
    final JsonObject fixedDueDateSchedules = new JsonObject();

    if(id != null) {
      fixedDueDateSchedules.put("id", id.toString());
    }

    fixedDueDateSchedules.put("name", this.name);
    fixedDueDateSchedules.put("description", this.description);

    fixedDueDateSchedules.put("schedules",
      schedules.stream()
        .map(schedule -> {
          final JsonObject representation = new JsonObject();

          put(representation, "from", schedule.from);
          put(representation, "to", schedule.to);
          put(representation, "due", schedule.due);

          return representation;
      })
      .collect(Collectors.toList()));

    return fixedDueDateSchedules;
  }

  public FixedDueDateSchedulesBuilder withId(UUID id) {
    return new FixedDueDateSchedulesBuilder(
      id,
      this.name,
      this.description,
      this.schedules);
  }

  public FixedDueDateSchedulesBuilder withName(String name) {
    return new FixedDueDateSchedulesBuilder(
      this.id,
      name,
      this.description,
      this.schedules);
  }

  public FixedDueDateSchedulesBuilder withDescription(String description) {
    return new FixedDueDateSchedulesBuilder(
      this.id,
      this.name,
      description,
      this.schedules);
  }

  public FixedDueDateSchedulesBuilder addSchedule(FixedDueDateSchedule schedule) {
    final Set<FixedDueDateSchedule> newSchedules = new HashSet<>(this.schedules);

    newSchedules.add(schedule);

    return new FixedDueDateSchedulesBuilder(
      this.id,
      this.name,
      description,
      newSchedules);
  }
}
