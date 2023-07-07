package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class TlrSettingsConfigurationBuilder extends JsonBuilder implements Builder {
  private final boolean titleLevelRequestsFeatureEnabled;
  private final boolean createTitleLevelRequestsByDefault;
  private final boolean tlrHoldShouldFollowCirculationRules;
  private final UUID confirmationPatronNoticeTemplateId;
  private final UUID cancellationPatronNoticeTemplateId;
  private final UUID expirationPatronNoticeTemplateId;

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    request.put("titleLevelRequestsFeatureEnabled", titleLevelRequestsFeatureEnabled);
    request.put("createTitleLevelRequestsByDefault", createTitleLevelRequestsByDefault);
    request.put("tlrHoldShouldFollowCirculationRules", tlrHoldShouldFollowCirculationRules);
    request.put("confirmationPatronNoticeTemplateId", confirmationPatronNoticeTemplateId);
    request.put("cancellationPatronNoticeTemplateId", cancellationPatronNoticeTemplateId);
    request.put("expirationPatronNoticeTemplateId", expirationPatronNoticeTemplateId);

    return request;
  }
}
