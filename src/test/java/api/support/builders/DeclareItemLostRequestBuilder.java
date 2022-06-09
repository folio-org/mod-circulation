package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;

import java.time.ZonedDateTime;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class DeclareItemLostRequestBuilder extends JsonBuilder implements Builder {
  private final String loanId;
  private final ZonedDateTime dateTime;
  private final String comment;
  private final String servicePointId;

  public DeclareItemLostRequestBuilder() {
    this(null, getZonedDateTime(), null, null);
  }

  public String getLoanId() {
    return loanId;
  }

  public ZonedDateTime getDateTime() {
    return dateTime;
  }

  public String getComment() {
    return comment;
  }

  public DeclareItemLostRequestBuilder(String loanId, ZonedDateTime dateTime,
    String comment, String servicePointId) {

    this.loanId = loanId;
    this.dateTime = dateTime;
    this.comment = comment;
    this.servicePointId = servicePointId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    write(request, "declaredLostDateTime", formatDateTimeOptional(this.dateTime));
    write(request, "comment", this.comment);
    write(request, "servicePointId", this.servicePointId);

    return request;
  }

   public DeclareItemLostRequestBuilder forLoanId(UUID id) {
    return new DeclareItemLostRequestBuilder(id.toString(), dateTime, comment, servicePointId);
  }

  public DeclareItemLostRequestBuilder on(ZonedDateTime dateTime) {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, comment, servicePointId);
  }

  public DeclareItemLostRequestBuilder withComment(String comment) {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, comment, servicePointId);
  }

  public DeclareItemLostRequestBuilder withNoComment() {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, "", servicePointId);
  }

  public DeclareItemLostRequestBuilder withServicePointId(UUID servicePointId) {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, comment, servicePointId.toString());
  }

  public static DeclareItemLostRequestBuilder forLoan(UUID loanId) {
    return new DeclareItemLostRequestBuilder()
      .forLoanId(loanId)
      .withServicePointId(UUID.randomUUID())
      .withComment("Declaring item lost")
      .on(getZonedDateTime());
  }
}
