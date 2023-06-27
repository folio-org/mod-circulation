package org.folio.circulation.domain.configuration;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;

@AllArgsConstructor
@Getter
@ToString
public class CheckoutLockConfiguration {

  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final boolean isCheckOutLockFeatureEnabled;
  private final int noOfRetryAttempts;
  private final int retryInterval;
  private final int lockTtl;

  public static CheckoutLockConfiguration from(JsonObject jsonObject) {
    try {
      return new CheckoutLockConfiguration(
        getBooleanProperty(jsonObject, "checkOutLockFeatureEnabled"),
        getIntegerProperty(jsonObject, "noOfRetryAttempts", 30),
        getIntegerProperty(jsonObject, "retryInterval", 250),
        getIntegerProperty(jsonObject, "lockTtl", 2000)
      );
    } catch (Exception ex) {
      log.error("Failed to parse checkOutLockFeature setting configuration", ex);
      return null;
    }
  }
}
