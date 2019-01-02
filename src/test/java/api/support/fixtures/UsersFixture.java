package api.support.fixtures;

import static api.support.fixtures.UserExamples.basedUponJamesRodwell;
import static api.support.fixtures.UserExamples.basedUponJessicaPontefract;
import static api.support.fixtures.UserExamples.basedUponRebeccaStuart;
import static api.support.fixtures.UserExamples.basedUponStevenJones;
import static java.util.function.Function.identity;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.UserBuilder;
import api.support.http.ResourceClient;

public class UsersFixture {
  private final RecordCreator userRecordCreator;

  public UsersFixture(ResourceClient usersClient) {
    this.userRecordCreator = new RecordCreator(usersClient);
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

    return userRecordCreator.createIfAbsent("JESSICA",
      basedUponJessicaPontefract().create());
  }

  public IndividualResource james()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.createIfAbsent("JAMES", basedUponJamesRodwell().create());
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

    return userRecordCreator.createIfAbsent("REBECCA",
      additionalProperties.apply(basedUponRebeccaStuart()).create());
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

    return userRecordCreator.createIfAbsent("STEVE",
      additionalUserProperties.apply(basedUponStevenJones()).create());
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

    return userRecordCreator.createIfAbsent("CHARLOTTE",
      additionalConfiguration.apply(UserExamples.basedUponCharlotteBroadwell()).create());
  }

}
