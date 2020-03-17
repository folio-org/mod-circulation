package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.junit.Assert.assertEquals;

import java.util.Collections;

import api.support.builders.ConfigRecordBuilder;
import api.support.builders.ConfigurationBuilder;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.joda.time.DateTimeZone;
import org.junit.BeforeClass;
import org.junit.Test;

public class ConfigurationServiceTest {

  private static final String US_LOCALE = "en-US";
  private static final String VALUE = "value";
  private static final Integer DEFAULT_TIMEOUT_CONFIGURATION = 3;

  private static ConfigurationService service;

  @BeforeClass
  public static void before() {
    service = new ConfigurationService();
  }

  @Test
  public void testUtcTimeZone() {
    String timeZoneValue = getTimezoneValue("UTC");
    JsonObject jsonObject = getJsonObject(timeZoneValue);

    assertEquals(DateTimeZone.UTC, service.findDateTimeZone(jsonObject));
  }

  @Test
  public void testEuropeTimeZone() {
    String zone = "Europe/Kiev";
    String timeZoneValue = getTimezoneValue(zone);
    JsonObject jsonObject = getJsonObject(timeZoneValue);

    assertEquals(DateTimeZone.forID(zone), service.findDateTimeZone(jsonObject));
  }

  @Test
  public void testEmptyTimeZoneValue() {
    String timeZoneValue = getTimezoneValue("");
    JsonObject jsonObject = getJsonObject(timeZoneValue);

    assertEquals(DateTimeZone.UTC, service.findDateTimeZone(jsonObject));
  }

  @Test
  public void testEmptyJsonValue() {
    JsonObject jsonObject = getJsonObject("");

    assertEquals(DateTimeZone.UTC, service.findDateTimeZone(jsonObject));
  }

  @Test
  public void testEmptyJson() {
    JsonObject jsonObject = new JsonObject();

    assertEquals(DateTimeZone.UTC, service.findDateTimeZone(jsonObject));
  }

  @Test
  public void shouldCorrectParseIfCheckoutTimeoutDurationIsInteger() {
    JsonObject jsonConfig = new JsonObject()
      .put(VALUE, getJsonConfigWithCheckoutTimeoutDurationAsInteger());
    List<Configuration> records = Collections.singletonList(new Configuration(jsonConfig));

    Integer actualSessionTimeout = service.findSessionTimeout(records);

    assertEquals(actualSessionTimeout, new Integer(1));
  }

  @Test
  public void shouldCorrectParseIfCheckoutTimeoutDurationIsString() {
    JsonObject jsonConfig = new JsonObject()
      .put(VALUE, getJsonConfigWithCheckoutTimeoutDurationAsString("1"));
    List<Configuration> records = Collections.singletonList(new Configuration(jsonConfig));

    Integer actualSessionTimeout = service.findSessionTimeout(records);

    assertEquals(actualSessionTimeout, new Integer(1));
  }

  @Test
  public void shouldUseDefaultCheckoutTimeoutDurationWhenConfiguredValueIsNotAnInteger() {
    JsonObject jsonConfig = new JsonObject()
      .put(VALUE, getJsonConfigWithCheckoutTimeoutDurationAsString("test"));
    List<Configuration> records = Collections.singletonList(new Configuration(jsonConfig));

    Integer actualSessionTimeout = service.findSessionTimeout(records);

    assertEquals(actualSessionTimeout, DEFAULT_TIMEOUT_CONFIGURATION);
  }

  private JsonObject getJsonObject(String timeZoneValue) {
    ConfigRecordBuilder config = new ConfigRecordBuilder(timeZoneValue);
    return new ConfigurationBuilder(Collections.singletonList(config)).create();
  }

  private String getTimezoneValue(String timezone) {
    final JsonObject encodedValue = new JsonObject();
    write(encodedValue, "locale", US_LOCALE);
    write(encodedValue, "timezone", timezone);
    return encodedValue.toString();
  }

  private String getJsonConfigWithCheckoutTimeoutDurationAsString(String checkoutTimeoutDuration) {
    final JsonObject encodedValue = new JsonObject();
    write(encodedValue, "checkoutTimeoutDuration", checkoutTimeoutDuration);
    write(encodedValue, "checkoutTimeout", true);
    return encodedValue.toString();
  }

  private String getJsonConfigWithCheckoutTimeoutDurationAsInteger() {
    final JsonObject encodedValue = new JsonObject();
    write(encodedValue, "checkoutTimeoutDuration", 1);
    write(encodedValue, "checkoutTimeout", true);
    return encodedValue.toString();
  }
}
