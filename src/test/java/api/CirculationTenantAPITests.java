package api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.folio.HttpStatus;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.APITests;
import api.support.fakes.FakePubSub;

public class CirculationTenantAPITests extends APITests {

  @Test
  public void tenantAPIFailsWhenCantRegisterInPubSub() {
    FakePubSub.setFailPubSubRegistration(true);

    Response response = tenantAPIFixture.postTenant();
    assertThat(response.getStatusCode(), is(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  public void tenantAPISucceedsWhenCanRegisterInPubSub() {
    Response response = tenantAPIFixture.postTenant();
    assertThat(response.getStatusCode(), is(HttpStatus.HTTP_CREATED.toInt()));
  }

}
