package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public class ProxyRelationship {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String NOTIFICATIONS_SENT_TO_PROPERTY_NAME = "notificationsTo";
  private static final String EXPIRATION_DATE_PROPERTY_NAME = "expirationDate";
  private static final String STATUS_PROPERTY_NAME = "status";

  private final ZonedDateTime expirationDate;
  private final boolean active;
  private final String notificationsSentTo;

  public ProxyRelationship(JsonObject representation) {
    this.active = getActive(representation);
    this.expirationDate = getExpirationDate(representation);
    this.notificationsSentTo = representation.getString(NOTIFICATIONS_SENT_TO_PROPERTY_NAME);
  }

  public boolean isActive() {
    boolean expired = expirationDate != null
      && isBeforeMillis(expirationDate, ClockUtil.getZonedDateTime());

    return active && !expired;
  }

  public boolean notificationsSentToSponsor() {
    return equalsIgnoreCase(notificationsSentTo, "Sponsor");
  }

  public boolean notificationsSentToProxy() {
    return equalsIgnoreCase(notificationsSentTo, "Proxy");
  }

  private boolean getActive(JsonObject representation) {
    log.debug("getActive:: parameters representation: {}", () -> representation);

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
    return equalsIgnoreCase(status, "Active");
  }
}
