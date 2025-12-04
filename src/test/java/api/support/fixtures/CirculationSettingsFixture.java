package api.support.fixtures;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.configuration.CirculationSettingName;

import api.support.builders.CirculationSettingBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;

public class CirculationSettingsFixture {

  private final ResourceClient circulationSettingsClient;
  private static final Map<String, UUID> namesToIds = new HashMap<>();

  public CirculationSettingsFixture() {
    circulationSettingsClient = ResourceClient.forCirculationSettings();
  }

  public IndividualResource create(CirculationSettingBuilder builder) {
    requireNonNull(builder.getName());
    if (builder.getId() == null) {
      builder = builder.withId(randomUUID());
    }
    String settingName = builder.getValue().getString("name");
    delete(settingName);
    IndividualResource createdSetting = circulationSettingsClient.create(builder);
    namesToIds.put(settingName, createdSetting.getId());
    return createdSetting;
  }

  public void delete(CirculationSettingName settingName) {
    delete(settingName.getValue());
  }

  public void delete(String settingName) {
    UUID value = namesToIds.get(settingName);
    if (value != null) {
      circulationSettingsClient.delete(value);
      namesToIds.remove(settingName);
    }
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
      .withId(randomUUID())
      .withName("loan_history")
      .withValue(configBuilder.create()));
  }
}
