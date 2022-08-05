package org.folio.circulation.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.format.DateTimeFormatter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.json.JsonObject;

class LoanTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "2020-01-13T12:34:56.123456+0000",
    "2020-01-13T12:34:56.123456",
    "2020-01-13T12:34:56+00:00",
    "2020-01-13T12:34:56.000Z",
    "2020-01-13T12:34:56Z",
    "2020-01-13T12:34:56"
  })
  void getLoanDateCanParseDatesInVariousFormats(String loanDate) {
    String formattedLoanDate = Loan.from(new JsonObject().put("loanDate", loanDate))
      .getLoanDate()
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"));

    assertEquals("2020-01-13T12:34:56Z", formattedLoanDate);
  }
}