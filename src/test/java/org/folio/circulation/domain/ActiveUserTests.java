package org.folio.circulation.domain;

import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import api.support.builders.UserBuilder;

class ActiveUserTests {
  @Test
  void userIsActiveWhenActivePropertyIsTrue() {
    final User activeUser = new User(new UserBuilder()
      .active()
      .create());

    assertThat(activeUser.isActive(), is(true));
    assertThat(activeUser.isInactive(), is(false));
  }

  @Test
  void userIsInactiveWhenActivePropertyIsFalse() {
    final User activeUser = new User(new UserBuilder()
      .inactive()
      .create());

    assertThat(activeUser.isActive(), is(false));
    assertThat(activeUser.isInactive(), is(true));
  }

  @Test
  void userIsInactiveWhenExpiredInThePast() {
    final User activeUser = new User(new UserBuilder()
      .active()
      .expires(getZonedDateTime().minusDays(10))
      .create());

    assertThat(activeUser.isActive(), is(false));
    assertThat(activeUser.isInactive(), is(true));
  }

  @Test
  void userIsActiveWhenExpiresInTheFuture() {
    final User activeUser = new User(new UserBuilder()
      .active()
      .expires(getZonedDateTime().plusDays(30))
      .create());

    assertThat(activeUser.isActive(), is(true));
    assertThat(activeUser.isInactive(), is(false));
  }

  @Test
  void userIsActiveWhenDoesNotExpire() {
    final User activeUser = new User(new UserBuilder()
      .active()
      .noExpiration()
      .create());

    assertThat(activeUser.isActive(), is(true));
    assertThat(activeUser.isInactive(), is(false));
  }
}
