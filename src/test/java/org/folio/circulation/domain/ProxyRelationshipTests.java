package org.folio.circulation.domain;

import api.support.builders.ProxyRelationshipBuilder;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class ProxyRelationshipTests {
  @Test
  @Parameters({
    "false",
    "true"
  })
  public void shouldBeActiveWhenActiveAndDoesNotExpire(boolean useMetaObject) {
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

  @Test
  @Parameters({
    "false",
    "true"
  })
  public void shouldBeActiveWhenActiveAndNotExpired(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .active()
        .expires(DateTime.now().plusWeeks(3))
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(true));
  }

  @Test
  @Parameters({
    "false",
    "true"
  })
  public void shouldBeInactiveWhenActiveAndExpired(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .active()
        .expires(DateTime.now().minusMonths(2))
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(false));
  }

  @Test
  @Parameters({
    "false",
    "true"
  })
  public void shouldBeInactiveWhenInactiveAndDoesNotExpire(boolean useMetaObject) {
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


  @Test
  @Parameters({
    "false",
    "true"
  })
  public void shouldBeInactiveWhenInactiveAndNotExpired(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .inactive()
        .expires(DateTime.now().plusWeeks(3))
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(false));
  }


  @Test
  @Parameters({
    "false",
    "true"
  })
  public void shouldBeInactiveWhenInactiveAndExpired(boolean useMetaObject) {
    final ProxyRelationship relationship = new ProxyRelationship(
      new ProxyRelationshipBuilder()
        .proxy(UUID.randomUUID())
        .sponsor(UUID.randomUUID())
        .inactive()
        .expires(DateTime.now().minusMonths(2))
        .useMetaObject(useMetaObject)
        .create());

    assertThat(relationship.isActive(), is(false));
  }
}
