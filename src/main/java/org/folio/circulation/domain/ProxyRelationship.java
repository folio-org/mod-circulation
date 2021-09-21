package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class ProxyRelationship {

  private final DateTime expirationDate;
  private final boolean active;

  public ProxyRelationship(JsonObject representation) {
    this.active = getActive(representation);
    this.expirationDate = getExpirationDate(representation);

  }

  private boolean getActive(JsonObject representation) {
    final String STATUS_PROPERTY_NAME = "status";

    if(representation.containsKey(STATUS_PROPERTY_NAME)) {
      return convertStatusToActive(
        representation.getString(STATUS_PROPERTY_NAME));
    }
    else {
      return convertStatusToActive(
        getNestedStringProperty(representation, "meta", STATUS_PROPERTY_NAME));
    }
  }

  private DateTime getExpirationDate(JsonObject representation) {
    final String EXPIRATION_DATE_PROPERTY_NAME = "expirationDate";

    if(representation.containsKey(EXPIRATION_DATE_PROPERTY_NAME) ) {
      return getDateTimeProperty(representation,
        EXPIRATION_DATE_PROPERTY_NAME);
    }
    else {
      return getNestedDateTimeProperty(representation, "meta",
        EXPIRATION_DATE_PROPERTY_NAME);
    }
  }

  private boolean convertStatusToActive(String status) {
    return StringUtils.equalsIgnoreCase(status, "Active");
  }

  public boolean isActive() {
      boolean expired = expirationDate != null
        && isBeforeMillis(expirationDate, ClockUtil.getDateTime());

      return active && !expired;
  }
}
