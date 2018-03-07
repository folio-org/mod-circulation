package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class UsersFixture {
  private final ResourceClient usersClient;

  public UsersFixture(ResourceClient usersClient) {
    this.usersClient = usersClient;
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

    return usersClient.create(UserExamples.basedUponStevenJones());
  }

  public IndividualResource Charlotte()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return usersClient.create(UserExamples.basedUponCharlotteBroadwell());
  }
}
