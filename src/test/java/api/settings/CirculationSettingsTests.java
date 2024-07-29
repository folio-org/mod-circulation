package api.settings;

import static api.support.http.InterfaceUrls.circulationSettingsUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CirculationSettingBuilder;
import api.support.http.CqlQuery;
import io.vertx.core.json.JsonObject;

class CirculationSettingsTests extends APITests {

  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String ERRORS = "errors";
  public static final String MESSAGE = "message";
  public static final String INVALID_JSON_MESSAGE = "Circulation setting JSON is invalid";

  @Test
  void crudOperationsTest() {
    // Testing POST method
    final var setting = circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("initial-name")
      .withValue(new JsonObject().put("initial-key", "initial-value")));
    final var settingId = setting.getId();

    // Testing GET (individual setting) method
    final var settingById = circulationSettingsClient.get(settingId);
    assertThat(settingById.getJson().getString(NAME), is("initial-name"));
    assertThat(settingById.getJson().getJsonObject(VALUE).getString("initial-key"),
      is("initial-value"));

    // Testing GET (all) method
    final var anotherSetting = circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("another-name")
      .withValue(new JsonObject().put("another-key", "another-value")));
    final var allSettings = circulationSettingsClient.getMany(CqlQuery.noQuery());
    assertThat(allSettings.size(), is(2));

    // Testing DELETE method
    circulationSettingsClient.delete(anotherSetting.getId());
    final var allSettingsAfterDeletion = circulationSettingsClient.getMany(CqlQuery.noQuery());
    assertThat(allSettingsAfterDeletion.size(), is(1));
    assertThat(allSettingsAfterDeletion.getFirst().getString(NAME), is("initial-name"));
    assertThat(allSettingsAfterDeletion.getFirst().getJsonObject(VALUE).getString("initial-key"),
      is("initial-value"));

    // Testing PUT method
    circulationSettingsClient.replace(settingId, new CirculationSettingBuilder()
      .withId(settingId)
      .withName("new-name")
      .withValue(new JsonObject().put("new-key", "new-value")));

    final var updatedSetting = circulationSettingsClient.get(settingId);

    assertThat(updatedSetting.getJson().getString(NAME), is("new-name"));
    assertThat(updatedSetting.getJson().getJsonObject(VALUE).getString("new-key"),
      is("new-value"));
  }

  @Test
  void invalidRequestsTest() {
    circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("initial-name")
      .withValue(new JsonObject().put("initial-key", "initial-value")));

    // Testing GET with wrong UUID
    restAssuredClient.get(circulationSettingsUrl("/" + randomId()), 404,
      "get-circulation-setting");

    // Testing GET with invalid ID (not a UUID)
    var getErrors = restAssuredClient.get(circulationSettingsUrl("/not-a-uuid"), 422,
      "get-circulation-setting");
    assertThat(getErrors.getJson().getJsonArray(ERRORS).getJsonObject(0).getString(MESSAGE),
      is("Circulation setting ID is not a valid UUID"));

    // Testing DELETE with invalid ID
    restAssuredClient.delete(circulationSettingsUrl("/" + randomId()), 204,
      "delete-circulation-setting");

    // Testing PUT with malformed JSON
    var putErrors = restAssuredClient.put("{\"invalid-field\": \"invalid-value\"}",
      circulationSettingsUrl("/" + randomId()), 422, "put-circulation-setting");
    assertThat(putErrors.getJson().getJsonArray(ERRORS).getJsonObject(0).getString(MESSAGE),
      is(INVALID_JSON_MESSAGE));

    var putErrorsNoValue = restAssuredClient.put("{\"name\": \"test-name\"}",
      circulationSettingsUrl("/" + randomId()), 422, "put-circulation-setting");
    assertThat(putErrorsNoValue.getJson().getJsonArray(ERRORS).getJsonObject(0).getString(MESSAGE),
      is(INVALID_JSON_MESSAGE));

    // Testing POST with malformed JSON
    var postErrors = restAssuredClient.post("{\"invalid-field\": \"invalid-value\"}",
      circulationSettingsUrl(""), 422, "put-circulation-setting");
    assertThat(postErrors.getJson().getJsonArray(ERRORS).getJsonObject(0).getString(MESSAGE),
      is(INVALID_JSON_MESSAGE));

    var postErrorsNoValue = restAssuredClient.put("{\"name\": \"test-name\"}",
      circulationSettingsUrl("/" + randomId()), 422, "put-circulation-setting");
    assertThat(postErrorsNoValue.getJson().getJsonArray(ERRORS).getJsonObject(0).getString(MESSAGE),
      is(INVALID_JSON_MESSAGE));
  }

  @Test
  void enableRequestPrintDetailsSettingTest() {
    final var setting = circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("EnableRequestPrintDetails")
      .withValue(new JsonObject().put("EnableRequestPrint", true)));
    final var settingId = setting.getId();

    final var settingById = circulationSettingsClient.get(settingId);
    assertThat(settingById.getJson().getString(NAME), is("EnableRequestPrintDetails"));
    assertThat(settingById.getJson().getJsonObject(VALUE).getString("EnableRequestPrint"),
      is("true"));

    circulationSettingsClient.replace(settingId, new CirculationSettingBuilder()
      .withId(settingId)
      .withName("EnableRequestPrint")
      .withValue(new JsonObject().put("EnableRequestPrint", true)));
    final var updatedSetting = circulationSettingsClient.get(settingId);
    assertThat(updatedSetting.getJson().getString(NAME), is("EnableRequestPrint"));

    circulationSettingsClient.delete(setting.getId());
    final var allSettingsAfterDeletion = circulationSettingsClient.getMany(CqlQuery.noQuery());
    assertThat(allSettingsAfterDeletion.size(), is(0));
  }
}
