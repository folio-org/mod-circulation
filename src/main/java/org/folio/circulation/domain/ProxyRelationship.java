package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public class ProxyRelationship {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ZonedDateTime expirationDate;
  private final boolean active;

  public ProxyRelationship(JsonObject representation) {
    this.active = getActive(representation);
    this.expirationDate = getExpirationDate(representation);

  }

  private boolean getActive(JsonObject representation) {
    log.debug("getActive:: parameters representation: {}", () -> representation);
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

  private ZonedDateTime getExpirationDate(JsonObject representation) {
    log.debug("getExpirationDate:: parameters representation: {}", () -> representation);
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
        && isBeforeMillis(expirationDate, ClockUtil.getZonedDateTime());

      return active && !expired;
  }
}
