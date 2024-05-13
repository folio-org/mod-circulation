package api;

import api.support.APITests;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static api.support.http.InterfaceUrls.itemsByInstanceUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ItemsByInstanceResourceTest extends APITests {
  @Test
  void canGetInstanceById() {
    UUID instanceId = UUID.randomUUID();
    searchFixture.basedUponDunkirk(instanceId);
    Response response = get(instanceId.toString(), 200);
    assertThat(response.getStatusCode(), is(200));
  }

  private Response get(String instanceId, int expectedStatusCode) {
    return restAssuredClient.get(itemsByInstanceUrl(instanceId), expectedStatusCode,
      "items-by-instance-request");
  }
}
