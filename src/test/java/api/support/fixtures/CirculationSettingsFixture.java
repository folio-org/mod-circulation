package api.support.fixtures;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.configuration.CirculationSettingName;

import api.support.builders.CirculationSettingBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class CirculationSettingsFixture {

  private final ResourceClient circulationSettingsClient;
  private static Map<String, UUID> namesToIds = new HashMap<>();

  public CirculationSettingsFixture() {
    circulationSettingsClient = ResourceClient.forCirculationSettings();
  }

  public IndividualResource create(CirculationSettingBuilder builder) {
    if (builder.getId() == null) {
      builder = builder.withId(UUID.randomUUID());
    }
    JsonObject setting = builder.create();
    String settingName = setting.getString("name");
    requireNonNull(settingName);
    delete(settingName);
    IndividualResource createdSetting = circulationSettingsClient.create(setting);
    namesToIds.put(settingName, createdSetting.getId());
    return createdSetting;
  }

  public void delete(CirculationSettingName settingName) {
    delete(settingName.getValue());
  }

  public void delete(String settingName) {
    Optional.ofNullable(namesToIds.get(settingName))
      .ifPresent(circulationSettingsClient::delete);
  }

  public IndividualResource setScheduledNoticesProcessingLimit(int limit) {
    return setScheduledNoticesProcessingLimit(String.valueOf(limit));
  }

  public IndividualResource setScheduledNoticesProcessingLimit(String limit) {
    return create(CirculationSettingExamples.scheduledNoticesLimit(limit));
  }

  public IndividualResource setPrintHoldRequests(boolean value) {
    return create(CirculationSettingExamples.printHoldRequests(value));
  }

  public IndividualResource createLoanHistorySettings(LoanHistoryConfigurationBuilder configBuilder) {
    return create(new CirculationSettingBuilder()
      .withId(UUID.randomUUID())
      .withName("loan_history")
      .withValue(configBuilder.create()));
  }
}
