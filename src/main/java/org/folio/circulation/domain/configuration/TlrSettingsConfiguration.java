package org.folio.circulation.domain.configuration;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
public class TlrSettingsConfiguration {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @ToString.Include
  private final boolean titleLevelRequestsFeatureEnabled;
  private final boolean createTitleLevelRequestsByDefault;
  private final boolean tlrHoldShouldFollowCirculationRules;
  private final UUID confirmationPatronNoticeTemplateId;
  private final UUID cancellationPatronNoticeTemplateId;
  private final UUID expirationPatronNoticeTemplateId;

  public static TlrSettingsConfiguration from(JsonObject jsonObject) {
    log.debug("from:: {}", jsonObject);
    try {
      return new TlrSettingsConfiguration(
        getBooleanProperty(jsonObject, "titleLevelRequestsFeatureEnabled"),
        getBooleanProperty(jsonObject, "createTitleLevelRequestsByDefault"),
        getBooleanProperty(jsonObject, "tlrHoldShouldFollowCirculationRules"),
        getUUIDProperty(jsonObject, "confirmationPatronNoticeTemplateId"),
        getUUIDProperty(jsonObject, "cancellationPatronNoticeTemplateId"),
        getUUIDProperty(jsonObject, "expirationPatronNoticeTemplateId")
      );
    }
    catch (IllegalArgumentException e) {
      log.error("Failed to parse TLR setting configuration");
      return null;
    }
  }

  public static TlrSettingsConfiguration defaultSettings() {
    log.info("defaultSettings:: building default TLR settings");
    return new TlrSettingsConfiguration(false, false, false, null, null, null);
  }
}
