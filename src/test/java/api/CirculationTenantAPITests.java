package api;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.folio.circulation.support.http.client.Response;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.fakes.FakePubSub;

public class CirculationTenantAPITests extends APITests {

  @Before
  public void init() {
    FakePubSub.setFailPubSubRegistration(false);
    FakePubSub.setFailPubSubUnregistering(false);
  }

  @Test
  public void postTenantFailsWhenCanNotRegisterInPubSub() {
    FakePubSub.setFailPubSubRegistration(true);

    Response response = tenantAPIFixture.postTenant();
    assertThat(response.getStatusCode(), is(HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  public void postTenantSucceedsWhenCanRegisterInPubSub() {
    Response response = tenantAPIFixture.postTenant();
    assertThat(response.getStatusCode(), is(HTTP_CREATED.toInt()));
  }

  @Test
  public void deleteTenantFailsWhenCanNotUnregisterFromPubSub() {
    FakePubSub.setFailPubSubUnregistering(true);

    Response response = tenantAPIFixture.deleteTenant();
    assertThat(response.getStatusCode(), is(HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  public void deleteTenantSucceedsWhenCanUnregisterFromPubSub() {
    Response response = tenantAPIFixture.deleteTenant();
    assertThat(response.getStatusCode(), is(HTTP_NO_CONTENT.toInt()));
  }

}
