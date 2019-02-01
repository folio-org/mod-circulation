package api.support.fixtures;

import static api.support.fixtures.UserExamples.basedUponCharlotteBroadwell;
import static api.support.fixtures.UserExamples.basedUponJamesRodwell;
import static api.support.fixtures.UserExamples.basedUponJessicaPontefract;
import static api.support.fixtures.UserExamples.basedUponRebeccaStuart;
import static api.support.fixtures.UserExamples.basedUponStevenJones;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
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

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    userRecordCreator.cleanUp();
  }

  public IndividualResource jessica()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.createIfAbsent(basedUponJessicaPontefract()
        .inGroupFor(patronGroupsFixture.regular()));
  }

  public IndividualResource james()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.createIfAbsent(basedUponJamesRodwell()
      .inGroupFor(patronGroupsFixture.regular()));
  }

  public IndividualResource rebecca()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return rebecca(identity());
  }
  
  public IndividualResource rebecca(
    Function<UserBuilder, UserBuilder> additionalProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.createIfAbsent(
      additionalProperties.apply(basedUponRebeccaStuart()
        .inGroupFor(patronGroupsFixture.regular())));
  }

  public IndividualResource steve()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return steve(identity());
  }

  public IndividualResource steve(
    Function<UserBuilder, UserBuilder> additionalUserProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.createIfAbsent(
      additionalUserProperties.apply(basedUponStevenJones()
        .inGroupFor(patronGroupsFixture.regular())));
  }

  public IndividualResource charlotte()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return charlotte(identity());
  }

  public IndividualResource charlotte(
    Function<UserBuilder, UserBuilder> additionalConfiguration)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.createIfAbsent(
      additionalConfiguration.apply(basedUponCharlotteBroadwell()
        .inGroupFor(patronGroupsFixture.regular())));
  }

  public void remove(IndividualResource user)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    userRecordCreator.delete(user);
  }
}
