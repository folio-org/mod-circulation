package api.support.builders;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@AllArgsConstructor
@NoArgsConstructor
@With
public class FeefineActionsBuilder extends JsonBuilder implements Builder {

  private String id;
  private ZonedDateTime dateAction = ClockUtil.getZonedDateTime();
  private Double balance;
  private Double actionAmount;
  private String paymentMethod;
  private String actionType;
  private String createdAt;
  private UUID accountId;
  private String comments;

  @Override
  public JsonObject create() {
    JsonObject object = new JsonObject();

    write(object, "id", id);
    write(object, "dateAction", formatDateTimeOptional(dateAction));
    write(object, "balance", balance);
    write(object, "amountAction", actionAmount);
    write(object, "paymentMethod", paymentMethod);
    write(object, "typeAction", actionType);
    write(object, "createdAt", createdAt);
    write(object, "source", "Admin, Admin");
    write(object, "accountId", accountId);
    write(object, "comments", comments);

    return object;
  }

}
