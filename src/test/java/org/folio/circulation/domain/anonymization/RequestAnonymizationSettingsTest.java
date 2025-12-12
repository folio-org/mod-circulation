package org.folio.circulation.domain.anonymization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class RequestAnonymizationSettingsTest {

  @Test
  void defaultSettingsAreNeverWithZeroHours() {
    RequestAnonymizationSettings s = RequestAnonymizationSettings.defaultSettings();

    assertEquals(RequestAnonymizationSettings.Mode.NEVER, s.getMode());
    assertEquals(0, s.getDelay());
    assertEquals(RequestAnonymizationSettings.DelayUnit.HOURS, s.getDelayUnit());
  }

  @Test
  void fromNullJsonReturnsDefaultSettings() {
    RequestAnonymizationSettings s = RequestAnonymizationSettings.from(null);

    assertEquals(RequestAnonymizationSettings.defaultSettings().toString(), s.toString());
  }

  @Test
  void parsesImmediatelyMode() {
    JsonObject json = new JsonObject()
      .put("mode", "immediately")
      .put("delay", 0)
      .put("delayUnit", "hours");

    RequestAnonymizationSettings s = RequestAnonymizationSettings.from(json);

    assertTrue(s.isImmediately());
    assertEquals(0, s.getDelay());
    assertEquals(RequestAnonymizationSettings.DelayUnit.HOURS, s.getDelayUnit());
  }

  @Test
  void parsesDelayedModeAndDelayUnitCaseInsensitively() {
    JsonObject json = new JsonObject()
      .put("mode", "DELAYED")
      .put("delay", 5)
      .put("delayUnit", "Days");

    RequestAnonymizationSettings s = RequestAnonymizationSettings.from(json);

    assertTrue(s.isDelayed());
    assertEquals(5, s.getDelay());
    assertEquals(RequestAnonymizationSettings.DelayUnit.DAYS, s.getDelayUnit());
  }

  @Test
  void negativeDelayIsClampedToZero() {
    RequestAnonymizationSettings s = new RequestAnonymizationSettings(
      RequestAnonymizationSettings.Mode.DELAYED,
      -10,
      RequestAnonymizationSettings.DelayUnit.HOURS
    );

    assertEquals(0, s.getDelay());
  }

  @Test
  void toJsonRoundTrip() {
    RequestAnonymizationSettings original = new RequestAnonymizationSettings(
      RequestAnonymizationSettings.Mode.DELAYED,
      3,
      RequestAnonymizationSettings.DelayUnit.MONTHS
    );

    RequestAnonymizationSettings roundTrip = RequestAnonymizationSettings.from(original.toJson());

    assertEquals(original.getMode(), roundTrip.getMode());
    assertEquals(original.getDelay(), roundTrip.getDelay());
    assertEquals(original.getDelayUnit(), roundTrip.getDelayUnit());
  }
}
