package api.support.builders;

import static org.folio.circulation.resources.ChangeDueDateResource.DUE_DATE;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class ChangeDueDateRequestBuilder implements Builder {
  private final DateTime dueDate;
  private final String loanId;

  public ChangeDueDateRequestBuilder() {
    this(null, DateTime.now(DateTimeZone.UTC));
  }

  private ChangeDueDateRequestBuilder(
    String loanId, DateTime dueDate) {

    this.dueDate = dueDate;
    this.loanId = loanId;
  }

  public ChangeDueDateRequestBuilder withDueDate(DateTime dateTime) {
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

  public DateTime getDueDate() {
    return dueDate;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    if (dueDate != null) {
      request.put(DUE_DATE, dueDate.toString());
    }

    return request;
  }
}
