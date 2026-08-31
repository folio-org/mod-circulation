package org.folio.circulation;

import static org.folio.circulation.EventConsumerVerticle.MODULE_NAME;
import static org.folio.circulation.EventConsumerVerticle.REAL_MODULE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class EventConsumerVerticleModuleVersionTest {
  private static final String MODULE_VERSION_RESOURCE = "/module-version.properties";

  @Test
  void realModuleIdContainsVersionFromFilteredResource() throws IOException {
    var properties = new Properties();
    try (var stream = getClass().getResourceAsStream(MODULE_VERSION_RESOURCE)) {
      assertNotNull(stream);
      properties.load(stream);
    }

    String version = properties.getProperty("version");
    assertNotNull(version);
    assertFalse(version.contains("${"));
    assertEquals(MODULE_NAME + "-" + version.replace("-SNAPSHOT", ""), REAL_MODULE_ID);
  }
}
