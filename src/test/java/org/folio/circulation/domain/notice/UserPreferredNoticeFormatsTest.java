package org.folio.circulation.domain.notice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.folio.circulation.domain.User;
import org.junit.jupiter.api.Test;

import api.support.builders.UserBuilder;

class UserPreferredNoticeFormatsTest {

  @Test
  void mapsContactTypesToFormats() {
    assertThat(UserPreferredNoticeFormats.toFormat("002").orElseThrow(), is(NoticeFormat.EMAIL));
    assertThat(UserPreferredNoticeFormats.toFormat("001").orElseThrow(), is(NoticeFormat.PRINT));
    assertThat(UserPreferredNoticeFormats.toFormat("003").orElseThrow(), is(NoticeFormat.SMS));
  }

  @Test
  void ignoresUnknownOrBlankValues() {
    assertThat(UserPreferredNoticeFormats.toFormat("999").isEmpty(), is(true));
    assertThat(UserPreferredNoticeFormats.toFormat("").isEmpty(), is(true));
    assertThat(UserPreferredNoticeFormats.toFormat(null).isEmpty(), is(true));
  }

  @Test
  void readsAndDeduplicatesPreferredContactTypeIds() {
    var user = new User(new UserBuilder()
      .withPreferredContactTypeIds(List.of("002", "003", "002"))
      .create());

    assertThat(UserPreferredNoticeFormats.fromPreferredContactTypeIds(user),
      is(List.of(NoticeFormat.EMAIL, NoticeFormat.SMS)));
  }

  @Test
  void usesDeprecatedPreferredContactTypeIdAsFallback() {
    var user = new User(new UserBuilder()
      .withDeprecatedPreferredContactTypeId("003")
      .create());

    assertThat(UserPreferredNoticeFormats.fromDeprecatedPreferredContactTypeId(user).orElseThrow(),
      is(NoticeFormat.SMS));
  }

  @Test
  void returnsEmptyListForNullUser() {
    assertThat(UserPreferredNoticeFormats.fromPreferredContactTypeIds(null), is(List.of()));
  }
}
