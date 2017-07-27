package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Random;
import java.util.UUID;

public class LoanRequestBuilder {

  private final UUID id;
  private final UUID itemId;
  private final UUID userId;
  private final DateTime loanDate;
  private final String status;
  private DateTime returnDate;
  private final String action;
  private final DateTime dueDate;

  public LoanRequestBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
      new DateTime(2017, 03, 06, 16, 04, 43), null, "Open", null, "checkedout"
    );
  }

  private LoanRequestBuilder(
    UUID id,
    UUID itemId,
    UUID userId,
    DateTime loanDate,
    DateTime dueDate, String status,
    DateTime returnDate,
    String action) {

    this.id = id;
    this.itemId = itemId;
    this.userId = userId;
    this.loanDate = loanDate;
    this.status = status;
    this.returnDate = returnDate;
    this.action = action;
    this.dueDate = dueDate;
  }

  public JsonObject create() {

    JsonObject loanRequest = new JsonObject();

    if(id != null) {
      loanRequest.put("id", id.toString());
    }

    loanRequest
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("loanDate", loanDate.toString(ISODateTimeFormat.dateTime()))
      .put("status", new JsonObject().put("name", status));

    if(action != null) {
      loanRequest.put("action", action);
    }

    if(dueDate != null) {
      loanRequest.put("dueDate",
        dueDate.toString(ISODateTimeFormat.dateTime()));
    }

    if(status == "Closed") {
      loanRequest.put("returnDate",
        returnDate.toString(ISODateTimeFormat.dateTime()));
    }

    return loanRequest;
  }

  public LoanRequestBuilder withRandomPastLoanDate() {
    Random random = new Random();

    return withLoanDate(DateTime.now().minusDays(random.nextInt(10)));
  }

  public LoanRequestBuilder withLoanDate(DateTime loanDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      loanDate, this.dueDate, this.status, this.returnDate, this.action);
  }

  public LoanRequestBuilder withUserId(UUID userId) {
    return new LoanRequestBuilder(this.id, this.itemId, userId,
      this.loanDate, this.dueDate, this.status, this.returnDate, this.action);
  }

  public LoanRequestBuilder withStatus(String status) {

    DateTime defaultedReturnDate = this.returnDate != null
      ? this.returnDate
      : this.loanDate.plusDays(1).plusHours(4);

    String action = null;

    switch(status) {
      case "Open":
        action = "checkedout";
        break;
      case "Closed":
        action = "checkedin";
        break;
    }

    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, status, defaultedReturnDate, action);
  }

  public LoanRequestBuilder withId(UUID id) {
    return new LoanRequestBuilder(id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate, this.action);
  }

  public LoanRequestBuilder withItemId(UUID itemId) {
    return new LoanRequestBuilder(this.id, itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate, this.action);
  }

  public LoanRequestBuilder withReturnDate(DateTime returnDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, returnDate, this.action);
  }

  public LoanRequestBuilder withItem(IndividualResource item) {
    return new LoanRequestBuilder(this.id, item.getId(), this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate, this.action);
  }

  public LoanRequestBuilder withDueDate(DateTime dueDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, dueDate, this.status, this.returnDate, this.action);
  }

  public LoanRequestBuilder dueIn(Period period) {
    if(this.loanDate == null) {
      throw new IllegalStateException(
        "Cannot use period to specify due when no loan date specified");
    }

    DateTime calculatedDueDate = this.loanDate.plus(period);

    return withDueDate(calculatedDueDate);
  }
}
