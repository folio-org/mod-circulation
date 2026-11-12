package org.folio.circulation;

import static org.folio.circulation.EventConsumerVerticle.MODULE_NAME;
import static org.folio.circulation.EventConsumerVerticle.RULES_CONSUMER_MODULE_ID;
import static org.folio.circulation.EventConsumerVerticle.genericConsumerGroupId;
import static org.folio.circulation.domain.EventType.FEE_FINE_BALANCE_CHANGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class EventConsumerVerticleModuleVersionTest {
  private static final String MODULE_VERSION_RESOURCE = "/module-version.properties";

  @Test
  void rulesConsumerModuleIdContainsVersionFromFilteredResource() throws IOException {
    var properties = new Properties();
    try (var stream = getClass().getResourceAsStream(MODULE_VERSION_RESOURCE)) {
      assertNotNull(stream);
      properties.load(stream);
    }

    String version = properties.getProperty("version");
    assertNotNull(version);
    assertFalse(version.contains("${"));
    assertEquals(RULES_CONSUMER_MODULE_ID,
      MODULE_NAME + "-" + version.replace("-SNAPSHOT", ""));
  }

  @Test
  void genericConsumerGroupIdDoesNotContainModuleVersion() {
    assertEquals("FEE_FINE_BALANCE_CHANGED.mod-circulation",
      genericConsumerGroupId(FEE_FINE_BALANCE_CHANGED));
  }
}
