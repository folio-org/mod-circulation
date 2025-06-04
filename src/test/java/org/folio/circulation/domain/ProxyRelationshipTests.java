package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.builders.ProxyRelationshipBuilder;

class ProxyRelationshipTests {
  @ParameterizedTest
  @ValueSource(strings = {
    "false",
    "true"
  })
  void shouldBeActiveWhenActiveAndDoesNotExpire(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .active()
        .doesNotExpire()
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(true));
  }

  @ParameterizedTest
  @CsvSource({
    "Sponsor, false",
    "Proxy, true"
  })
  void shouldBeActiveAndNotificationsSentToSponsorOrProxy(String sentTo, boolean sentToProxy) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .active()
        .doesNotExpire()
        .notificationsSentTo(sentTo)
        .create());

    assertThat(relationship.isActive(), is(true));
    assertThat(relationship.notificationsSentToSponsor(), is(!sentToProxy));
    assertThat(relationship.notificationsSentToProxy(), is(sentToProxy));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "false",
    "true"
  })
  void shouldBeActiveWhenActiveAndNotExpired(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .active()
        .expires(ClockUtil.getZonedDateTime().plusWeeks(3))
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "false",
    "true"
  })
  void shouldBeInactiveWhenActiveAndExpired(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .active()
        .expires(ClockUtil.getZonedDateTime().minusMonths(2))
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "false",
    "true"
  })
  void shouldBeInactiveWhenInactiveAndDoesNotExpire(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .inactive()
        .doesNotExpire()
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(false));
  }


  @ParameterizedTest
  @ValueSource(strings = {
    "false",
    "true"
  })
  void shouldBeInactiveWhenInactiveAndNotExpired(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .inactive()
        .expires(ClockUtil.getZonedDateTime().plusWeeks(3))
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(false));
  }


  @ParameterizedTest
  @ValueSource(strings = {
    "false",
    "true"
  })
  void shouldBeInactiveWhenInactiveAndExpired(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .inactive()
        .expires(ClockUtil.getZonedDateTime().minusMonths(2))
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(false));
  }
}
