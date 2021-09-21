package api.support.builders;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;

import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
public class RequestByInstanceIdRequestBuilder implements Builder {
  private final DateTime requestDate;
  private final UUID requesterId;
  private final UUID instanceId;
  private final DateTime requestExpirationDate;
  private final UUID pickupServicePointId;
  private final String patronComments;

  public RequestByInstanceIdRequestBuilder() {
    this(ClockUtil.getDateTime(), null, null, ClockUtil.getDateTime().plusWeeks(1), null, null);
  }

  @Override
  public JsonObject create() {
    JsonObject requestBody = new JsonObject();

    write(requestBody, "instanceId", instanceId);
    write(requestBody, "requestDate", formatDateTimeOptional(requestDate));
    write(requestBody, "requesterId", requesterId);
    write(requestBody, "pickupServicePointId", pickupServicePointId);
    write(requestBody, "fulfilmentPreference", "Hold Shelf");
    write(requestBody, "requestExpirationDate", formatDateTimeOptional(requestExpirationDate));
    write(requestBody, "patronComments", patronComments);

    return requestBody;
  }
}
