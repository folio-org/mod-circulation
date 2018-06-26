package org.folio.circulation.domain;

import api.support.builders.UserBuilder;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UserTests {
  @Test
  public void personalNameComesFromPersonalDetails() {
    final User activeUser = new User(new UserBuilder()
      .withName("Jones", "Clarissa")
      .withUsername("cjones")
      .create());

    assertThat(activeUser.getPersonalName(), is("Jones, Clarissa"));
  }

  @Test
  public void personalNameIsUsernameWhenNoPersonalDetails() {
    final User activeUser = new User(new UserBuilder()
      .withNoPersonalDetails()
      .withUsername("cjones")
      .create());

    assertThat(activeUser.getPersonalName(), is("cjones"));
  }

}
