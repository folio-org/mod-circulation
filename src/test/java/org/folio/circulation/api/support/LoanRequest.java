package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Random;
import java.util.UUID;

public class LoanRequest {

  public static JsonObject create() {
    return create(UUID.randomUUID(), UUID.randomUUID(),
      UUID.randomUUID(), DateTime.parse("2017-03-06T16:04:43.000+02:00",
        ISODateTimeFormat.dateTime()), "Open");
  }

  public static JsonObject create(
    UUID id,
    UUID itemId,
    UUID userId,
    DateTime loanDate,
    String statusName) {

    JsonObject loanRequest = new JsonObject();

    if(id != null) {
      loanRequest.put("id", id.toString());
    }

    loanRequest
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("loanDate", loanDate.toString(ISODateTimeFormat.dateTime()))
      .put("status", new JsonObject().put("name", statusName));

    if(statusName == "Closed") {
      loanRequest.put("returnDate",
        loanDate.plusDays(1).plusHours(4).toString(ISODateTimeFormat.dateTime()));
    }

    return loanRequest;
  }

  public static JsonObject loanRequest(UUID userId, String statusName) {
    Random random = new Random();

    return create(UUID.randomUUID(), UUID.randomUUID(),
      userId, DateTime.now().minusDays(random.nextInt(10)), statusName);
  }

  public static JsonObject loanRequest(DateTime loanDate) {
    return create(UUID.randomUUID(), UUID.randomUUID(),
      UUID.randomUUID(), loanDate, "Open");
  }
}
