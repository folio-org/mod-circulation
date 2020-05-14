package api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.APITests;

public class CirculationTenantAPITests extends APITests {

  @Test
  public void tenantApiTest() {
    Response response = tenantAPIFixture.postTenant();
    assertThat(response.getStatusCode(), is(201));
  }

}
