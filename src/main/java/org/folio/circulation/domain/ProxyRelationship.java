package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

class ProxyRelationship extends JsonObject {
  ProxyRelationship(JsonObject representation) {
    super(representation.getMap());
  }

  boolean isActive() {
    if (containsKey("meta")) {
      final JsonObject meta = getJsonObject("meta");

      boolean notExpired = true;
      boolean active = true;

      if (meta.containsKey("expirationDate")) {
        final DateTime expirationDate = DateTime.parse(
          meta.getString("expirationDate"));

        notExpired = expirationDate.isAfter(DateTime.now());
      }

      if (meta.containsKey("status")) {
        active = StringUtils.equalsIgnoreCase(meta.getString("status"), "Active");
      }

      return active && notExpired;
    } else {
      return true;
    }
  }
}
