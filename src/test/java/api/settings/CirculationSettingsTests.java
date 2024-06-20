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

  @Test
  void crudOperationsTest() {
    // Testing POST method
    final var setting = circulationSettingsClient.create(new CirculationSettingBuilder()
      .withName("initial-name")
      .withValue(new JsonObject().put("initial-key", "initial-value")));
    final var settingId = setting.getId();

    // Testing GET (individual setting) method
    final var settingById = circulationSettingsClient.get(settingId);
    assertThat(settingById.getJson().getString("name"), is("initial-name"));
    assertThat(settingById.getJson().getJsonObject("value").getString("initial-key"),
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
    assertThat(allSettingsAfterDeletion.getFirst().getString("name"), is("initial-name"));
    assertThat(allSettingsAfterDeletion.getFirst().getJsonObject("value").getString("initial-key"),
      is("initial-value"));

    // Testing PUT method
    circulationSettingsClient.replace(settingId, new CirculationSettingBuilder()
      .withId(settingId)
      .withName("new-name")
      .withValue(new JsonObject().put("new-key", "new-value")));

    final var updatedSetting = circulationSettingsClient.get(settingId);

    assertThat(updatedSetting.getJson().getString("name"), is("new-name"));
    assertThat(updatedSetting.getJson().getJsonObject("value").getString("new-key"),
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
    assertThat(getErrors.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Circulation setting ID is not a valid UUID"));

    // Testing DELETE with invalid ID
    restAssuredClient.delete(circulationSettingsUrl("/" + randomId()), 204,
      "delete-circulation-setting");

    // Testing PUT with malformed JSON
    var putErrors = restAssuredClient.put("{\"invalid-field\": \"invalid-value\"}",
      circulationSettingsUrl("/" + randomId()), 422, "put-circulation-setting");
    assertThat(putErrors.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Circulation setting JSON is malformed"));

    // Testing POST with malformed JSON
    var postErrors = restAssuredClient.post("{\"invalid-field\": \"invalid-value\"}",
      circulationSettingsUrl(""), 422, "put-circulation-setting");
    assertThat(postErrors.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Circulation setting JSON is malformed"));
  }
}
