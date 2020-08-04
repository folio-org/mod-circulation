package org.folio.circulation.infrastructure.storage;

import static org.junit.Assert.assertEquals;

import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import org.folio.circulation.domain.Configuration;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.junit.Test;

public class ConfigurationRepositoryTest {
  private LoanAnonymizationConfiguration getFirstConfiguration(String... values) {
    Collection<Configuration> collection = new ArrayList<>(values.length);
    for (String value : values) {
      collection.add(new Configuration(new JsonObject().put("value", value)));
    }
    return ConfigurationRepository.getFirstConfiguration(collection);
  }

  @Test
  public void firstConfigurationFromNone() {
    assertEquals(new JsonObject(), getFirstConfiguration().getRepresentation());
  }

  @Test
  public void firstConfigurationFromTwo() {
    JsonObject actual = getFirstConfiguration("{\"foo\":1}", "{\"bar\":2}").getRepresentation();
    assertEquals(new JsonObject().put("foo", 1), actual);
  }
}
