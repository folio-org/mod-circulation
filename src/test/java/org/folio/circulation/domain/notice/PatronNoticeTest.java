package org.folio.circulation.domain.notice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;
import java.util.stream.Stream;

import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.json.JsonObject;

class PatronNoticeTest {

  @ParameterizedTest
  @MethodSource("noticeFormats")
  void buildsNoticeUsingConfigurationFormat(NoticeFormat format,
    String deliveryChannel, String outputFormat) {

    var configuration = new NoticeConfiguration(
      "template",
      format,
      NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER,
      Period.days(1),
      false,
      null,
      true);

    var notice = new PatronNotice(
      "recipient",
      new JsonObject().put("key", "value"),
      configuration);

    assertThat(notice.getDeliveryChannel(), is(deliveryChannel));
    assertThat(notice.getOutputFormat(), is(outputFormat));
  }

  static Stream<Arguments> noticeFormats() {
    return Stream.of(
      Arguments.of(NoticeFormat.EMAIL, "email", "text/html"),
      Arguments.of(NoticeFormat.SMS, "sms", "text/plain"),
      Arguments.of(NoticeFormat.PRINT, "mail", "text/html")
    );
  }

  @Test
  void buildsEmailNotice() {
    var notice = PatronNotice.buildEmail(
      "recipient",
      UUID.randomUUID(),
      new JsonObject());

    assertThat(notice.getDeliveryChannel(), is("email"));
    assertThat(notice.getOutputFormat(), is("text/html"));
  }

  @Test
  void serializesNoticeContext() {
    var notice = PatronNotice.buildEmail(
      "recipient",
      UUID.randomUUID(),
      new JsonObject().put("key", "value"));

    var json = notice.toJson();

    assertThat(json.getString("recipientId"), is("recipient"));
    assertThat(json.getString("deliveryChannel"), is("email"));
    assertThat(json.getString("outputFormat"), is("text/html"));
    assertThat(json.getJsonObject("context").getString("key"), is("value"));
  }
}
