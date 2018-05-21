package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import static org.folio.circulation.support.DefensiveJsonPropertyFetcher.getNestedDateTimeProperty;
import static org.folio.circulation.support.DefensiveJsonPropertyFetcher.getNestedStringProperty;

class ProxyRelationship {

  private final DateTime expirationDate;
  private final boolean active;

  ProxyRelationship(JsonObject representation) {
    active = convertStatusToActive(
      getNestedStringProperty(representation, "meta", "status"));

    expirationDate = getNestedDateTimeProperty(representation, "meta",
      "expirationDate");
  }

  private boolean convertStatusToActive(String status) {
    return StringUtils.equalsIgnoreCase(status, "Active");
  }

  boolean isActive() {
      boolean notExpired = true;

      if(expirationDate != null) {
        notExpired = expirationDate.isAfter(DateTime.now());
      }

      return active && notExpired;
  }
}
