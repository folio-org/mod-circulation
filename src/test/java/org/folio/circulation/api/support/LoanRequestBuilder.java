package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
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

  public LoanRequestBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
      DateTime.parse("2017-03-06T16:04:43.000+02:00",
        ISODateTimeFormat.dateTime()), "Open", null);
  }

  private LoanRequestBuilder(
    UUID id,
    UUID itemId,
    UUID userId,
    DateTime loanDate,
    String status,
    DateTime returnDate) {

    this.id = id;
    this.itemId = itemId;
    this.userId = userId;
    this.loanDate = loanDate;
    this.status = status;
    this.returnDate = returnDate;
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
      loanDate, this.status, this.returnDate);
  }

  public LoanRequestBuilder withUserId(UUID userId) {
    return new LoanRequestBuilder(this.id, this.itemId, userId,
      this.loanDate, this.status, this.returnDate);
  }

  public LoanRequestBuilder withStatus(String status) {

    DateTime defaultedReturnDate = this.returnDate != null
      ? this.returnDate
      : this.loanDate.plusDays(1).plusHours(4);

    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, status, defaultedReturnDate);
  }

  public LoanRequestBuilder withId(UUID id) {
    return new LoanRequestBuilder(id, this.itemId, this.userId,
      this.loanDate, this.status, this.returnDate);
  }

  public LoanRequestBuilder withItemId(UUID itemId) {
    return new LoanRequestBuilder(this.id, itemId, this.userId,
      this.loanDate, this.status, this.returnDate);
  }

  public LoanRequestBuilder withReturnDate(DateTime returnDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.status, returnDate);
  }

  public LoanRequestBuilder withItem(IndividualResource item) {
    return new LoanRequestBuilder(this.id, item.getId(), this.userId,
      this.loanDate, this.status, this.returnDate);
  }


}
