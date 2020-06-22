package api.support.fixtures;

import static api.support.http.InterfaceUrls.overrideRenewalByBarcodeUrl;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.RestAssuredClient;
import api.support.dto.Item;
import api.support.dto.OverrideRenewal;
import api.support.dto.User;
import api.support.spring.clients.ResourceClient;
import lombok.AllArgsConstructor;
import lombok.val;

@AllArgsConstructor
public final class OverrideRenewalFixture {
  private final RestAssuredClient restAssuredClient;
  private final ResourceClient<Item> itemsFixture;
  private final ResourceClient<User> usersFixture;

  public void overrideRenewalByBarcode(OverrideRenewal request) {
    restAssuredClient.post(request, overrideRenewalByBarcodeUrl(), 200, "override-renewal");
  }

  public void overrideRenewalByBarcode(IndividualResource loan, UUID servicePointId) {
    val itemId = loan.getJson().getString("itemId");
    val userId = loan.getJson().getString("userId");

    final Item item = itemsFixture.getById(itemId);
    final User user = usersFixture.getById(userId);

    overrideRenewalByBarcode(OverrideRenewal.builder()
      .itemBarcode(item.getBarcode())
      .userBarcode(user.getBarcode())
      .comment("Override renewal")
      .overrideServicePointId(servicePointId.toString())
      .build());
  }
}
