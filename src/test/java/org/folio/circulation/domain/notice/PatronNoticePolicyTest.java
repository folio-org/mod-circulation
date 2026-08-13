package org.folio.circulation.domain.notice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;

class PatronNoticePolicyTest {

  @Test
  void lookupNoticeConfigurationsReturnsAllMatchingEntriesInOrder() {
    var first = new NoticeConfiguration("t1", NoticeFormat.EMAIL, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);
    var second = new NoticeConfiguration("t2", NoticeFormat.SMS, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);
    var third = new NoticeConfiguration("t3", NoticeFormat.PRINT, NoticeEventType.CHECK_OUT,
      NoticeTiming.AFTER, Period.days(1), false, null, true);

    var policy = new PatronNoticePolicy(List.of(first, second, third));

    assertThat(policy.lookupNoticeConfigurations(NoticeEventType.CHECK_OUT), is(List.of(first, second, third)));
    assertThat(policy.lookupNoticeConfiguration(NoticeEventType.CHECK_OUT).orElseThrow(), is(first));
  }
}
