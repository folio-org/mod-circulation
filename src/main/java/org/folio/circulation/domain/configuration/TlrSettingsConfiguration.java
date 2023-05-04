package org.folio.circulation.domain.configuration;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

import static java.lang.String.format;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;

@AllArgsConstructor
@Getter
@ToString(onlyExplicitlyIncluded = true)
public class TlrSettingsConfiguration {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @ToString.Include
  private final boolean titleLevelRequestsFeatureEnabled;
  private final boolean createTitleLevelRequestsByDefault;
  private final UUID confirmationPatronNoticeTemplateId;
  private final UUID cancellationPatronNoticeTemplateId;
  private final UUID expirationPatronNoticeTemplateId;

  public static TlrSettingsConfiguration from(JsonObject jsonObject) {
    try {
      return new TlrSettingsConfiguration(
        getBooleanProperty(jsonObject, "titleLevelRequestsFeatureEnabled"),
        getBooleanProperty(jsonObject, "createTitleLevelRequestsByDefault"),
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
}
