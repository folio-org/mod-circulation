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

public class TenantActivationResourceTests extends APITests {

  @Before
  public void init() {
    FakePubSub.setFailPubSubRegistration(false);
    FakePubSub.setFailPubSubUnregistering(false);
  }

  @Test
  public void tenantActivationFailsWhenCannotRegisterWithPubSub() {
    FakePubSub.setFailPubSubRegistration(true);

    Response response = tenantAPIFixture.postTenant();
    assertThat(response.getStatusCode(), is(HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  public void tenantActivationSucceedsWhenCanRegisterInPubSub() {
    Response response = tenantAPIFixture.postTenant();
    assertThat(response.getStatusCode(), is(HTTP_CREATED.toInt()));
  }

  @Test
  public void tenantDeactivationFailsWhenCannotUnregisterWithPubSub() {
    FakePubSub.setFailPubSubUnregistering(true);

    Response response = tenantAPIFixture.deleteTenant();
    assertThat(response.getStatusCode(), is(HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  public void tenantDeactivationSucceedsWhenCanUnregisterInPubSub() {
    Response response = tenantAPIFixture.deleteTenant();
    assertThat(response.getStatusCode(), is(HTTP_NO_CONTENT.toInt()));
  }

}
