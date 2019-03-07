package org.folio.circulation.domain;

import api.support.builders.ConfigurationBuilder;
import api.support.builders.TimeZoneConfigBuilder;
import io.vertx.core.json.JsonObject;
import org.joda.time.DateTimeZone;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ConfigurationServiceTest {

  private static ConfigurationService service;

  @BeforeClass
  public static void before() {
    service = new ConfigurationService();
  }

  @Test
  public void testUtcTimeZone() {
    String timeZoneValue = "{\"locale\":\"en-US\",\"timezone\":\"UTC\"}";
    JsonObject jsonObject = getJsonObject(timeZoneValue);

    assertEquals(DateTimeZone.UTC, service.findDateTimeZone(jsonObject));
  }

  @Test
  public void testEuropeTimeZone() {
    String zone = "Europe/Kiev";
    String timeZoneValue = "{\"timezone\":\"%s\"}";
    JsonObject jsonObject = getJsonObject(String.format(timeZoneValue, zone));

    assertEquals(DateTimeZone.forID(zone), service.findDateTimeZone(jsonObject));
  }

  @Test
  public void testEmptyTimeZoneValue() {
    String timeZoneValue = "{\"timezone\":\"\"}";
    JsonObject jsonObject = getJsonObject(timeZoneValue);

    assertEquals(DateTimeZone.UTC, service.findDateTimeZone(jsonObject));
  }

  @Test
  public void testEmptyJsonValue() {
    String timeZoneValue = "";
    JsonObject jsonObject = getJsonObject(timeZoneValue);

    assertEquals(DateTimeZone.UTC, service.findDateTimeZone(jsonObject));
  }

  @Test
  public void testEmptyJson() {
    JsonObject jsonObject = new JsonObject();

    assertEquals(DateTimeZone.UTC, service.findDateTimeZone(jsonObject));
  }

  private JsonObject getJsonObject(String timeZoneValue) {
    TimeZoneConfigBuilder config = new TimeZoneConfigBuilder(timeZoneValue);
    return new ConfigurationBuilder(Collections.singletonList(config)).create();
  }
}
