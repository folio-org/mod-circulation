package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import api.support.builders.UserBuilder;

class UserTests {
  @Test
  void personalNameComesFromPersonalDetails() {
    final User activeUser = new User(new UserBuilder()
      .withName("Jones", "Clarissa")
      .withUsername("cjones")
      .create());

    assertThat(activeUser.getPersonalName(), is("Jones, Clarissa"));
  }

  @Test
  void personalNameIsUsernameWhenNoPersonalDetails() {
    final User activeUser = new User(new UserBuilder()
      .withNoPersonalDetails()
      .withUsername("cjones")
      .create());

    assertThat(activeUser.getPersonalName(), is("cjones"));
  }

  @Test
  void preferredContactTypeIdsAreReadInOrder() {
    var user = new User(new UserBuilder()
      .withPreferredContactTypeIds(List.of("002", "003"))
      .create());

    assertThat(user.getPreferredContactTypeIds(), contains("002", "003"));
  }

  @Test
  void preferredContactTypeIdsIgnoreNonStringEntries() {
    var user = new User(new JsonObject()
      .put("personal", new JsonObject()
        .put("preferredContactTypeIds", new JsonArray().add("002").add(1).add(new JsonObject()))));

    assertThat(user.getPreferredContactTypeIds(), contains("002"));
  }

  @Test
  void preferredContactTypeIdsAreEmptyWhenPersonalIsMissing() {
    User user = new User(new UserBuilder()
      .withNoPersonalDetails()
      .create());

    assertThat(user.getPreferredContactTypeIds(), empty());
  }

  @Test
  void deprecatedPreferredContactTypeIdIsReadWhenPresent() {
    var user = new User(new UserBuilder()
      .withDeprecatedPreferredContactTypeId("003")
      .create());

    assertThat(user.getPreferredContactTypeId(), is("003"));
  }

  @Test
  void deprecatedPreferredContactTypeIdIsNullWhenMissing() {
    var user = new User(new UserBuilder()
      .withNoPersonalDetails()
      .create());

    assertThat(user.getPreferredContactTypeId(), nullValue());
  }
}
