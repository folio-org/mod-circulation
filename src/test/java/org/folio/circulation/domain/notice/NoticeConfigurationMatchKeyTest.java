package org.folio.circulation.domain.notice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;

class NoticeConfigurationMatchKeyTest {

  @Test
  void ignoresTemplateIdAndFormat() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);
    var right = new NoticeConfiguration("t2", NoticeFormat.SMS, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);

    assertThat(left.matchKey(), is(right.matchKey()));
    assertThat(left.matchKey().hashCode(), is(right.matchKey().hashCode()));
  }

  @Test
  void ignoresTemplateIdAlone() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);
    var right = new NoticeConfiguration("t9", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);

    assertThat(left.matchKey(), is(right.matchKey()));
  }

  @Test
  void differsWhenTimingChanges() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);
    var right = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.BEFORE, Period.days(1), false, null, true);

    assertThat(left.matchKey().equals(right.matchKey()), is(false));
  }

  @Test
  void differsWhenTimingPeriodChanges() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);
    var right = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(2), false, null, true);

    assertThat(left.matchKey().equals(right.matchKey()), is(false));
  }

  @Test
  void differsWhenRecurringChanges() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT, null, false, null, true);
    var right = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT, null, true, Period.days(1), true);

    assertThat(left.matchKey().equals(right.matchKey()), is(false));
  }

  @Test
  void differsWhenRecurringPeriodChanges() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), true, Period.days(1), true);
    var right = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), true, Period.days(2), true);

    assertThat(left.matchKey().equals(right.matchKey()), is(false));
  }

  @Test
  void differsWhenSendInRealTimeChanges() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT, null, false, null, true);
    var right = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT, null, false, null, false);

    assertThat(left.matchKey().equals(right.matchKey()), is(false));
  }

  @Test
  void differsWhenEventTypeChanges() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT, null, false, null, true);
    var right = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_IN,
      NoticeTiming.UPON_AT, null, false, null, true);

    assertThat(left.matchKey().equals(right.matchKey()), is(false));
  }

  @Test
  void handlesNullPeriods() {
    var left = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT, null, false, null, true);
    var right = new NoticeConfiguration("t2", NoticeFormat.SMS, NoticeEventType.CHECK_OUT,
      NoticeTiming.UPON_AT, null, false, null, true);

    assertThat(left.matchKey(), is(right.matchKey()));
  }
}
