package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

public class JsonPropertyWriter {
  private JsonPropertyWriter() {

  }

  public static void write(
    JsonObject to, String propertyName,
    String value) {

    if(StringUtils.isNotBlank(value)) {
      to.put(propertyName, value);
    }
  }
}
