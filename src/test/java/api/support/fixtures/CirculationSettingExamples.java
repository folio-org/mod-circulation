package api.support.fixtures;

import java.util.UUID;

import api.support.builders.CirculationSettingBuilder;
import io.vertx.core.json.JsonObject;

public class CirculationSettingExamples {

  public static CirculationSettingBuilder scheduledNoticesLimit(String value) {
    return new CirculationSettingBuilder()
      .withId(UUID.randomUUID())
      .withName("noticesLimit")
      .withValue(new JsonObject().put("value", value));
  }

  public static CirculationSettingBuilder printHoldRequests(boolean value) {
    return new CirculationSettingBuilder()
      .withId(UUID.randomUUID())
      .withName("PRINT_HOLD_REQUESTS")
      .withValue(new JsonObject().put("printHoldRequestsEnabled", value));
  }

  public static CirculationSettingBuilder generalTlrSettings(boolean isTlrFeatureEnabled,
    boolean tlrHoldShouldFollowCirculationRules) {

    return new CirculationSettingBuilder()
      .withId(UUID.randomUUID())
      .withName("generalTlr")
      .withValue(new JsonObject()
        .put("titleLevelRequestsFeatureEnabled", isTlrFeatureEnabled)
        .put("createTitleLevelRequestsByDefault", false)
        .put("tlrHoldShouldFollowCirculationRules", tlrHoldShouldFollowCirculationRules));
  }

  public static CirculationSettingBuilder regularTlrSettings(UUID confirmationTemplateId,
    UUID cancellationTemplateId, UUID expirationTemplateId) {

    return new CirculationSettingBuilder()
      .withId(UUID.randomUUID())
      .withName("regularTlr")
      .withValue(new JsonObject()
        .put("confirmationPatronNoticeTemplateId", confirmationTemplateId)
        .put("cancellationPatronNoticeTemplateId", cancellationTemplateId)
        .put("expirationPatronNoticeTemplateId", expirationTemplateId));
  }

}
