package api.support.builders;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class DeclareItemLostRequestBuilder extends JsonBuilder implements Builder {
  private final String loanId;
  private final DateTime dateTime;
  private final String comment;
  private final String servicePointId;

  public DeclareItemLostRequestBuilder() {
    this(null, ClockUtil.getDateTime(), null, null);
  }

  public String getLoanId() {
    return loanId;
  }

  public DateTime getDateTime() {
    return dateTime;
  }

  public String getComment() {
    return comment;
  }

  public DeclareItemLostRequestBuilder(String loanId, DateTime dateTime,
    String comment, String servicePointId) {

    this.loanId = loanId;
    this.dateTime = dateTime;
    this.comment = comment;
    this.servicePointId = servicePointId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    write(request, "declaredLostDateTime", this.dateTime);
    write(request, "comment", this.comment);
    write(request, "servicePointId", this.servicePointId);

    return request;
  }

   public DeclareItemLostRequestBuilder forLoanId(UUID id) {
    return new DeclareItemLostRequestBuilder(id.toString(), dateTime, comment, servicePointId);
  }

  public DeclareItemLostRequestBuilder on(DateTime dateTime) {
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
      .on(ClockUtil.getDateTime());
  }
}
