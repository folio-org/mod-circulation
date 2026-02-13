package api.support.fixtures;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LocaleFixture {
  private static final String US_LOCALE = "en-US";
  private static final String DEFAULT_CURRENCY = "USD";

  private final ResourceClient localeClient;

  public LocaleFixture() {
    this.localeClient = ResourceClient.forLocale();
  }

  public void createLocaleSettingsForTimezone(String timezone) {
    localeClient.create(buildLocaleSettings(US_LOCALE, timezone, DEFAULT_CURRENCY));
  }

  public void createLocaleSettings(String locale, String timezone, String currency) {
    localeClient.create(buildLocaleSettings(locale, timezone, currency));
  }

  public void createLocaleSettingsWithNumberingSystem(String locale, String timezone,
      String currency, String numberingSystem) {
    JsonObject settings = buildLocaleSettings(locale, timezone, currency);
    settings.put("numberingSystem", numberingSystem);
    localeClient.create(settings);
  }

  private JsonObject buildLocaleSettings(String locale, String timezone, String currency) {
    return new JsonObject()
      .put("locale", locale)
      .put("timezone", timezone)
      .put("currency", currency);
  }

  public JsonObject get() {
    return localeClient.getAll().stream().findFirst().orElse(null);
  }

  public void delete() {
    localeClient.deleteAll();
  }
}
