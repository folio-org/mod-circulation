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

  @Test
  void modeFromAndToStorageValueAreCovered() {
    assertEquals(RequestAnonymizationSettings.Mode.IMMEDIATELY,
      RequestAnonymizationSettings.Mode.from("immediately"));
    assertEquals(RequestAnonymizationSettings.Mode.DELAYED,
      RequestAnonymizationSettings.Mode.from("delayed"));
    assertEquals(RequestAnonymizationSettings.Mode.NEVER,
      RequestAnonymizationSettings.Mode.from("never"));

    assertEquals(RequestAnonymizationSettings.Mode.NEVER,
      RequestAnonymizationSettings.Mode.from("unknown"));
    assertEquals(RequestAnonymizationSettings.Mode.NEVER,
      RequestAnonymizationSettings.Mode.from(null));

    assertEquals("immediately", RequestAnonymizationSettings.Mode.IMMEDIATELY.toStorageValue());
    assertEquals("delayed", RequestAnonymizationSettings.Mode.DELAYED.toStorageValue());
    assertEquals("never", RequestAnonymizationSettings.Mode.NEVER.toStorageValue());
  }

  @Test
  void delayUnitFromAndToStorageValueAreCovered() {
    assertEquals(RequestAnonymizationSettings.DelayUnit.MINUTES,
      RequestAnonymizationSettings.DelayUnit.from("minute"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.MINUTES,
      RequestAnonymizationSettings.DelayUnit.from("minutes"));

    assertEquals(RequestAnonymizationSettings.DelayUnit.DAYS,
      RequestAnonymizationSettings.DelayUnit.from("day"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.DAYS,
      RequestAnonymizationSettings.DelayUnit.from("days"));

    assertEquals(RequestAnonymizationSettings.DelayUnit.MONTHS,
      RequestAnonymizationSettings.DelayUnit.from("month"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.MONTHS,
      RequestAnonymizationSettings.DelayUnit.from("months"));

    assertEquals(RequestAnonymizationSettings.DelayUnit.YEARS,
      RequestAnonymizationSettings.DelayUnit.from("year"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.YEARS,
      RequestAnonymizationSettings.DelayUnit.from("years"));

    assertEquals(RequestAnonymizationSettings.DelayUnit.HOURS,
      RequestAnonymizationSettings.DelayUnit.from("hour"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.HOURS,
      RequestAnonymizationSettings.DelayUnit.from("hours"));

    assertEquals(RequestAnonymizationSettings.DelayUnit.MINUTES,
      RequestAnonymizationSettings.DelayUnit.from("minute(s)"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.DAYS,
      RequestAnonymizationSettings.DelayUnit.from("day(s)"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.MONTHS,
      RequestAnonymizationSettings.DelayUnit.from("month(s)"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.YEARS,
      RequestAnonymizationSettings.DelayUnit.from("year(s)"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.HOURS,
      RequestAnonymizationSettings.DelayUnit.from("hour(s)"));

    assertEquals(RequestAnonymizationSettings.DelayUnit.HOURS,
      RequestAnonymizationSettings.DelayUnit.from("nonsense"));
    assertEquals(RequestAnonymizationSettings.DelayUnit.HOURS,
      RequestAnonymizationSettings.DelayUnit.from(null));

    assertEquals("minutes", RequestAnonymizationSettings.DelayUnit.MINUTES.toStorageValue());
    assertEquals("days", RequestAnonymizationSettings.DelayUnit.DAYS.toStorageValue());
    assertEquals("months", RequestAnonymizationSettings.DelayUnit.MONTHS.toStorageValue());
    assertEquals("years", RequestAnonymizationSettings.DelayUnit.YEARS.toStorageValue());
    assertEquals("hours", RequestAnonymizationSettings.DelayUnit.HOURS.toStorageValue()); // hits default or HOURS
  }

  @Test
  void constructorDefaultsAndClampAreCovered() {
    RequestAnonymizationSettings s = new RequestAnonymizationSettings(null, -5, null);

    assertEquals(RequestAnonymizationSettings.Mode.NEVER, s.getMode());
    assertEquals(0, s.getDelay());
    assertEquals(RequestAnonymizationSettings.DelayUnit.HOURS, s.getDelayUnit());
  }



}
