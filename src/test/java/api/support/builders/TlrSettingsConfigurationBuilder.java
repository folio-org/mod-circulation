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
  private final UUID confirmationPatronNoticeTemplateId;
  private final UUID cancellationPatronNoticeTemplateId;
  private final UUID expirationPatronNoticeTemplateId;

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    put(request, "titleLevelRequestsFeatureEnabled", titleLevelRequestsFeatureEnabled);
    put(request, "createTitleLevelRequestsByDefault", createTitleLevelRequestsByDefault);
    put(request, "confirmationPatronNoticeTemplateId", confirmationPatronNoticeTemplateId);
    put(request, "cancellationPatronNoticeTemplateId", cancellationPatronNoticeTemplateId);
    put(request, "expirationPatronNoticeTemplateId", expirationPatronNoticeTemplateId);

    return request;
  }
}
