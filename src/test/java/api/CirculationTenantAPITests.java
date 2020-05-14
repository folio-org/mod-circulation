package api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.folio.circulation.support.http.client.Response;
import org.folio.util.pubsub.PubSubClientUtils;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;

import api.support.APITests;

@PrepareForTest(PubSubClientUtils.class)
public class CirculationTenantAPITests extends APITests {

  @Test
  public void tenantApiTest() {
    Response response = tenantAPIFixture.postTenant();
    assertThat(response.getStatusCode(), is(201));
  }

}
