package api.support.fixtures;

import static api.support.fixtures.UserExamples.basedUponJamesRodwell;
import static api.support.fixtures.UserExamples.basedUponJessicaPontefract;
import static api.support.fixtures.UserExamples.basedUponRebeccaStuart;
import static api.support.fixtures.UserExamples.basedUponStevenJones;
import static java.util.function.Function.identity;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;

import api.support.builders.ProxyRelationshipBuilder;
import api.support.builders.UserBuilder;
import api.support.http.ResourceClient;

public class UsersFixture {
  private final ResourceClient proxyRelationshipClient;
  private final RecordCreator userRecordCreator;

  public UsersFixture(
    ResourceClient usersClient,
    ResourceClient proxyRelationshipClient) {

    this.proxyRelationshipClient = proxyRelationshipClient;
    userRecordCreator = new RecordCreator(usersClient);
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    userRecordCreator.cleanUp();
  }

  public void expiredProxyFor(
    IndividualResource sponsor,
    IndividualResource proxy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    proxyFor(sponsor.getId(), proxy.getId(), DateTime.now().minusYears(1));
  }

  public void currentProxyFor(
    IndividualResource sponsor,
    IndividualResource proxy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    proxyFor(sponsor.getId(), proxy.getId(), DateTime.now().plusYears(1));
  }

  private void proxyFor(
    UUID sponsorUserId,
    UUID proxyUserId,
    DateTime expirationDate)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    proxyRelationshipClient.create(new ProxyRelationshipBuilder()
      .sponsor(sponsorUserId)
      .proxy(proxyUserId)
      .active()
      .expires(expirationDate));
  }

  public void inactiveProxyFor(
    IndividualResource sponsor,
    IndividualResource proxy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    proxyRelationshipClient.create(new ProxyRelationshipBuilder()
      .sponsor(sponsor.getId())
      .proxy(proxy.getId())
      .inactive()
      .expires(DateTime.now().plusYears(1)));
  }

  public void nonExpiringProxyFor(
    IndividualResource sponsor,
    IndividualResource proxy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    proxyRelationshipClient.create(new ProxyRelationshipBuilder()
      .sponsor(sponsor.getId())
      .proxy(proxy.getId())
      .active()
      .doesNotExpire());
  }

  public IndividualResource jessica()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.create(basedUponJessicaPontefract());
  }

  public IndividualResource james()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.create(basedUponJamesRodwell());
  }

  public IndividualResource rebecca()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.create(basedUponRebeccaStuart());
  }
  
  public IndividualResource rebecca(
    Function<UserBuilder, UserBuilder> additionalProperties)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.create(
      additionalProperties.apply(basedUponRebeccaStuart()));
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

    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return userRecordCreator.create(
      additionalUserProperties.apply(basedUponStevenJones()));
  }

  public IndividualResource charlotte()
    throws
    InterruptedException,
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

    return userRecordCreator.create(additionalConfiguration.apply(
      UserExamples.basedUponCharlotteBroadwell()));
  }
}
