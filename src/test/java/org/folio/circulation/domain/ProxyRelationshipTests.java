package org.folio.circulation.domain;

import api.support.builders.ProxyRelationshipBuilder;
import org.joda.time.DateTime;
import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ProxyRelationshipTests {
  @Test
  public void shouldBeActiveWhenActiveAndDoesNotExpire() {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
      .proxy(UUID.randomUUID())
      .sponsor(UUID.randomUUID())
      .active()
      .doesNotExpire()
      .create());

    assertThat(relationship.isActive(), is(true));
  }

  @Test
  public void shouldBeActiveWhenActiveAndNotExpired() {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .active()
        .expires(DateTime.now().plusWeeks(3))
        .create());

    assertThat(relationship.isActive(), is(true));
  }

  @Test
  public void shouldBeInactiveWhenActiveAndExpired() {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .active()
        .expires(DateTime.now().minusMonths(2))
        .create());

    assertThat(relationship.isActive(), is(false));
  }

  @Test
  public void shouldBeInactiveWhenInactiveAndDoesNotExpire() {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .inactive()
        .doesNotExpire()
        .create());

    assertThat(relationship.isActive(), is(false));
  }


  @Test
  public void shouldBeInactiveWhenInactiveAndNotExpired() {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .inactive()
        .expires(DateTime.now().plusWeeks(3))
        .create());

    assertThat(relationship.isActive(), is(false));
  }


  @Test
  public void shouldBeInactiveWhenInactiveAndExpired() {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .inactive()
        .expires(DateTime.now().minusMonths(2))
        .create());

    assertThat(relationship.isActive(), is(false));
  }
}
