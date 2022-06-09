package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;

public class ChangeDueDateRequestBuilder implements Builder {
  private final ZonedDateTime dueDate;
  private final String loanId;

  public ChangeDueDateRequestBuilder() {
    this(null, ClockUtil.getZonedDateTime());
  }

  private ChangeDueDateRequestBuilder(String loanId, ZonedDateTime dueDate) {
    this.dueDate = dueDate;
    this.loanId = loanId;
  }

  public ChangeDueDateRequestBuilder withDueDate(ZonedDateTime dateTime) {
    return new ChangeDueDateRequestBuilder(loanId, dateTime);
  }

  public ChangeDueDateRequestBuilder forLoan(String loanId) {
    return new ChangeDueDateRequestBuilder(loanId, dueDate);
  }

  public ChangeDueDateRequestBuilder forLoan(UUID loanId) {
    return new ChangeDueDateRequestBuilder(loanId.toString(), dueDate);
  }

  public String getLoanId() {
    return loanId;
  }

  public ZonedDateTime getDueDate() {
    return dueDate;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();
    write(request, "dueDate", dueDate);
    return request;
  }
}
