package api.support.fixtures;

import api.support.builders.UserBuilder;
import api.support.builders.ProxyRelationshipBuilder;
import api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

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

    proxyRelationshipClient.create(new ProxyRelationshipBuilder().
      withValidationFields(expirationDate.toString(), "Active",
        sponsorUserId.toString(), proxyUserId.toString()));
  }

  public void inactiveProxyFor(
    IndividualResource sponsor,
    IndividualResource proxy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    proxyRelationshipClient.create(new ProxyRelationshipBuilder().
      withValidationFields(DateTime.now().plusYears(1).toString(), "Inactive",
        sponsor.getId().toString(), proxy.getId().toString()));
  }

  public void nonExpiringProxyFor(
    IndividualResource sponsor,
    IndividualResource proxy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
      proxyRelationshipClient.create(new ProxyRelationshipBuilder().
        withValidationFields(null, "Active",
          sponsor.getId().toString(), proxy.getId().toString()));
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

    return usersClient.create(UserExamples.basedUponCharlotteBroadwell());
  }
}
