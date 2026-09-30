package org.folio.circulation.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ModuleInfo {

  private static final String MODULE_VERSION_RESOURCE = "/module-version.properties";

  public static String moduleVersion() {
    try (var stream = ModuleInfo.class.getResourceAsStream(MODULE_VERSION_RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("Missing module version resource: "
          + MODULE_VERSION_RESOURCE);
      }

      var properties = new Properties();
      properties.load(stream);
      String version = properties.getProperty("version");
      if (version == null || version.isBlank() || version.contains("${")) {
        throw new IllegalStateException("Invalid module version in "
          + MODULE_VERSION_RESOURCE + ": " + version);
      }

      return version.replace("-SNAPSHOT", "");
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read module version from "
        + MODULE_VERSION_RESOURCE, e);
    }
  }
}
