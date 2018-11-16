package api.support.fixtures;

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
  private final ResourceClient usersClient;
  private final ResourceClient proxyRelationshipClient;

  public UsersFixture(
    ResourceClient usersClient,
    ResourceClient proxyRelationshipClient) {

    this.usersClient = usersClient;
    this.proxyRelationshipClient = proxyRelationshipClient;
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

  public void proxyFor(
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

    return usersClient.create(UserExamples.basedUponJessicaPontefract());
  }

  public IndividualResource james()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return usersClient.create(UserExamples.basedUponJamesRodwell());
  }

  public IndividualResource rebecca()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return usersClient.create(UserExamples.basedUponRebeccaStuart());
  }

  public IndividualResource steve()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return steve(b -> b);
  }

  public IndividualResource steve(
    Function<UserBuilder, UserBuilder> additionalUserProperties)

    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final UserBuilder builder = additionalUserProperties.apply(
      UserExamples.basedUponStevenJones());

    return usersClient.create(builder);
  }



  public IndividualResource charlotte()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return charlotte(Function.identity());
  }

  public IndividualResource charlotte(
    Function<UserBuilder, UserBuilder> additionalConfiguration)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return usersClient.create(additionalConfiguration.apply(
      UserExamples.basedUponCharlotteBroadwell()));
  }

}
