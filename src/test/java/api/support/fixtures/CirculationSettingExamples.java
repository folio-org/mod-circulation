package api.support.fixtures;

import java.util.UUID;

import api.support.builders.CirculationSettingBuilder;
import io.vertx.core.json.JsonObject;

public class CirculationSettingExamples {

  public static CirculationSettingBuilder scheduledNoticesLimit(int value) {
    return scheduledNoticesLimit(String.valueOf(value));
  }

  public static CirculationSettingBuilder scheduledNoticesLimit(String value) {
    return new CirculationSettingBuilder()
      .withId(UUID.randomUUID())
      .withName("noticesLimit")
      .withValue(new JsonObject().put("value", value));
  }
}
