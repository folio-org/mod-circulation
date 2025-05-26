package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.vertx.core.json.JsonObject;

class ConfigurationServiceTest {

  private static final String VALUE = "value";
  private static final Integer DEFAULT_TIMEOUT_CONFIGURATION = 3;

  private static ConfigurationService service;


  @BeforeAll
  public static void before() {
    service = new ConfigurationService();
  }

  @Test
  void shouldUseConfiguredCheckoutTimeoutDurationWhenAnInteger() {
    JsonObject jsonConfig = new JsonObject()
      .put(VALUE, getJsonConfigWithCheckoutTimeoutDurationAsInteger());
    List<Configuration> records = Collections.singletonList(new Configuration(jsonConfig));

    Integer actualSessionTimeout = service.findSessionTimeout(records);

    assertEquals(actualSessionTimeout, Integer.valueOf(1));
  }

  @Test
  void shouldUseConfiguredCheckoutTimeoutDurationWhenIsAnIntegerString() {
    JsonObject jsonConfig = new JsonObject()
      .put(VALUE, getJsonConfigWithCheckoutTimeoutDurationAsString("1"));
    List<Configuration> records = Collections.singletonList(new Configuration(jsonConfig));

    Integer actualSessionTimeout = service.findSessionTimeout(records);

    assertEquals(actualSessionTimeout, Integer.valueOf(1));
  }

  @Test
  void shouldUseDefaultCheckoutTimeoutDurationWhenConfiguredValueIsNotAnInteger() {
    JsonObject jsonConfig = new JsonObject()
      .put(VALUE, getJsonConfigWithCheckoutTimeoutDurationAsString("test"));
    List<Configuration> records = Collections.singletonList(new Configuration(jsonConfig));

    Integer actualSessionTimeout = service.findSessionTimeout(records);

    assertEquals(DEFAULT_TIMEOUT_CONFIGURATION, actualSessionTimeout);
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
