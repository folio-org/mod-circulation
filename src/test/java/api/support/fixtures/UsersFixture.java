package api.support.fixtures;

import static api.support.fixtures.UserExamples.basedUponBobbyBibbin;
import static api.support.fixtures.UserExamples.basedUponCharlotteBroadwell;
import static api.support.fixtures.UserExamples.basedUponHenryHanks;
import static api.support.fixtures.UserExamples.basedUponJamesRodwell;
import static api.support.fixtures.UserExamples.basedUponJessicaPontefract;
import static api.support.fixtures.UserExamples.basedUponRebeccaStuart;
import static api.support.fixtures.UserExamples.basedUponStevenJones;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.function.Function;

import api.support.http.IndividualResource;

import api.support.APITestContext;
import api.support.builders.UserBuilder;
import api.support.http.ResourceClient;
import api.support.http.UserResource;

public class UsersFixture {
  private final RecordCreator userRecordCreator;
  private final PatronGroupsFixture patronGroupsFixture;

  public UsersFixture(ResourceClient usersClient, PatronGroupsFixture patronGroupsFixture) {
    this.userRecordCreator = new RecordCreator(usersClient,
      user -> getProperty(user, "username"));

    this.patronGroupsFixture = patronGroupsFixture;
  }

  public UserResource jessica() {
    return createIfAbsent(basedUponJessicaPontefract()
      .inGroupFor(patronGroupsFixture.regular()));
  }

  public UserResource james() {
    return createIfAbsent(basedUponJamesRodwell()
      .inGroupFor(patronGroupsFixture.regular()));
  }

  public UserResource rebecca() {
    return rebecca(identity());
  }

  public UserResource rebecca(Function<UserBuilder, UserBuilder> additionalConfiguration) {
    return createIfAbsent(additionalConfiguration.apply(basedUponRebeccaStuart()
      .inGroupFor(patronGroupsFixture.regular())));
  }

  public UserResource steve() {
    return steve(identity());
  }

  public UserResource steve(Function<UserBuilder, UserBuilder> additionalConfiguration) {
    return createIfAbsent(additionalConfiguration.apply(basedUponStevenJones()
      .inGroupFor(patronGroupsFixture.regular())));
  }

  public UserResource charlotte() {
    return charlotte(identity());
  }

  public UserResource charlotte(Function<UserBuilder, UserBuilder> additionalConfiguration) {
    return createIfAbsent(additionalConfiguration.apply(basedUponCharlotteBroadwell()
      .inGroupFor(patronGroupsFixture.regular())));
  }

  public UserResource undergradHenry() {
    return undergradHenry(identity());
  }

  public UserResource undergradHenry(Function<UserBuilder, UserBuilder> additionalConfiguration) {
    return createIfAbsent(additionalConfiguration.apply(basedUponHenryHanks()
      .inGroupFor(patronGroupsFixture.undergrad())));
  }

  public IndividualResource noUserGroupBob() {
    return noUserGroupBob(identity());
  }

  public UserResource noUserGroupBob(Function<UserBuilder, UserBuilder> additionalConfiguration) {
    return createIfAbsent(additionalConfiguration.apply(basedUponBobbyBibbin()));
  }

  public void remove(UserResource user) {
    userRecordCreator.delete(user);
  }

  public void defaultAdmin() {
    createIfAbsent(new UserBuilder()
      .withName("Admin", "Admin")
      .withNoBarcode()
      .withId(APITestContext.getUserId()));
  }

  private UserResource createIfAbsent(UserBuilder userBuilder) {
    return new UserResource(userRecordCreator.createIfAbsent(userBuilder));
  }
}
