package org.folio.circulation.domain.notice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoticeFormatTest {

  @Test
  void parsesKnownFormats() {
    assertThat(NoticeFormat.from("Email"), is(NoticeFormat.EMAIL));
    assertThat(NoticeFormat.from("SMS"), is(NoticeFormat.SMS));
    assertThat(NoticeFormat.from("Print"), is(NoticeFormat.PRINT));
    assertThat(NoticeFormat.from("bogus"), is(NoticeFormat.UNKNOWN));
  }

  @Test
  void exposesExpectedValues() {
    assertThat(NoticeFormat.EMAIL.getRepresentation(), is("Email"));
    assertThat(NoticeFormat.EMAIL.getDeliveryChannel(), is("email"));
    assertThat(NoticeFormat.EMAIL.getOutputFormat(), is("text/html"));

    assertThat(NoticeFormat.SMS.getRepresentation(), is("SMS"));
    assertThat(NoticeFormat.SMS.getDeliveryChannel(), is("sms"));
    assertThat(NoticeFormat.SMS.getOutputFormat(), is("text/plain"));

    assertThat(NoticeFormat.PRINT.getRepresentation(), is("Print"));
    assertThat(NoticeFormat.PRINT.getDeliveryChannel(), is("mail"));
    assertThat(NoticeFormat.PRINT.getOutputFormat(), is("text/html"));
  }

  @Test
  void knowsWhichFormatsAreDeliverable() {
    assertTrue(NoticeFormat.EMAIL.isDeliverable());
    assertTrue(NoticeFormat.SMS.isDeliverable());
    assertTrue(NoticeFormat.PRINT.isDeliverable());
    assertFalse(NoticeFormat.UNKNOWN.isDeliverable());
  }
}
