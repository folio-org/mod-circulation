package org.folio.circulation.support.json;

import static java.time.ZoneOffset.ofHours;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getOffsetDateTimeProperty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class DateAndTimeJsonPropertyFetcherTests {
  @Test
  void shouldReturnCorrectDateAndTimeWhenPropertyIsPresent() {
    final var json = new JsonObject();

    writeOffsetDateTimeProperty(json, "2020-11-18T22:11:34-04:00");

    final var expectedDateTime = OffsetDateTime.of(
      LocalDate.of(2020, 11, 18), LocalTime.of(22, 11, 34), ofHours(-4));

    assertThat(getOffsetDateTime(json), is(expectedDateTime));
  }

  @Test
  void shouldReturnNullWhenPropertyIsNotPresent() {
    final var json = new JsonObject();

    assertThat(getOffsetDateTime(json), nullValue());
  }

  @Test
  void shouldReturnNullWhenJsonObjectIsNull() {
    assertThat(getOffsetDateTime(null), nullValue());
  }

  @Test
  void shouldReturnNullWhenPropertyIsBlank() {
    final var json = new JsonObject();

    writeOffsetDateTimeProperty(json, "    ");

    assertThat(getOffsetDateTime(json), nullValue());
  }

  private void writeOffsetDateTimeProperty(JsonObject json, String value) {
    json.put("offsetDateTime", value);
  }

  private OffsetDateTime getOffsetDateTime(JsonObject json) {
    return getOffsetDateTimeProperty(json, "offsetDateTime");
  }
}
