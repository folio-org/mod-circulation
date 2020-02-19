package api.support.fixtures;

import static api.support.fixtures.UserExamples.basedUponBobbyBibbin;
import static api.support.fixtures.UserExamples.basedUponCharlotteBroadwell;
import static api.support.fixtures.UserExamples.basedUponHenryHanks;
import static api.support.fixtures.UserExamples.basedUponJamesRodwell;
import static api.support.fixtures.UserExamples.basedUponJessicaPontefract;
import static api.support.fixtures.UserExamples.basedUponRebeccaStuart;
import static api.support.fixtures.UserExamples.basedUponStevenJones;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.function.Function;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.UserBuilder;
import api.support.http.ResourceClient;

public class UsersFixture {
  private final RecordCreator userRecordCreator;
  private final PatronGroupsFixture patronGroupsFixture;

  public UsersFixture(
    ResourceClient usersClient,
    PatronGroupsFixture patronGroupsFixture) {

    this.userRecordCreator = new RecordCreator(usersClient,
      user -> getProperty(user, "username"));

    this.patronGroupsFixture = patronGroupsFixture;
  }

  public void cleanUp() {

    userRecordCreator.cleanUp();
  }

  public IndividualResource jessica() {

    return userRecordCreator.createIfAbsent(basedUponJessicaPontefract()
        .inGroupFor(patronGroupsFixture.regular()));
  }

  public IndividualResource james() {

    return userRecordCreator.createIfAbsent(basedUponJamesRodwell()
      .inGroupFor(patronGroupsFixture.regular()));
  }

  public IndividualResource rebecca() {

    return rebecca(identity());
  }

  public IndividualResource rebecca(
    Function<UserBuilder, UserBuilder> additionalProperties) {

    return userRecordCreator.createIfAbsent(
      additionalProperties.apply(basedUponRebeccaStuart()
        .inGroupFor(patronGroupsFixture.regular())));
  }

  public IndividualResource steve() {

    return steve(identity());
  }

  public IndividualResource steve(
    Function<UserBuilder, UserBuilder> additionalUserProperties) {

    return userRecordCreator.createIfAbsent(
      additionalUserProperties.apply(basedUponStevenJones()
        .inGroupFor(patronGroupsFixture.regular())));
  }

  public IndividualResource charlotte() {

    return charlotte(identity());
  }

  public IndividualResource charlotte(
    Function<UserBuilder, UserBuilder> additionalConfiguration) {

    return userRecordCreator.createIfAbsent(
      additionalConfiguration.apply(basedUponCharlotteBroadwell()
        .inGroupFor(patronGroupsFixture.regular())));
  }

  public IndividualResource undergradHenry() {

    return undergradHenry(identity());
  }

  public IndividualResource undergradHenry(
    Function<UserBuilder, UserBuilder> additionalUserProperties) {

    return userRecordCreator.createIfAbsent(
      additionalUserProperties.apply(basedUponHenryHanks()
        .inGroupFor(patronGroupsFixture.undergrad())));
  }

  public IndividualResource noUserGroupBob() {

    return noUserGroupBob(identity());
  }

  public IndividualResource noUserGroupBob(
    Function<UserBuilder, UserBuilder> additionalUserProperties) {

    return userRecordCreator.createIfAbsent(
      additionalUserProperties.apply(basedUponBobbyBibbin()));
  }

  public void remove(IndividualResource user) {

    userRecordCreator.delete(user);
  }
}
