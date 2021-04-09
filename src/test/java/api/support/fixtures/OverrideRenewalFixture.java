package api.support.fixtures;

import static api.support.http.InterfaceUrls.renewByBarcodeUrl;

import java.util.UUID;

import api.support.RestAssuredClient;
import api.support.builders.RenewBlockOverrideBuilder;
import api.support.builders.RenewBlockOverrides;
import api.support.dto.Item;
import api.support.dto.OverrideRenewal;
import api.support.dto.User;
import api.support.http.IndividualResource;
import api.support.spring.clients.ResourceClient;
import lombok.AllArgsConstructor;
import lombok.val;

@AllArgsConstructor
public final class OverrideRenewalFixture {
  private final RestAssuredClient restAssuredClient;
  private final ResourceClient<Item> itemsFixture;
  private final ResourceClient<User> usersFixture;

  public void overrideRenewalByBarcode(OverrideRenewal request) {
    restAssuredClient.post(request, renewByBarcodeUrl(), 200, "renewal-by-barcode-request");
  }

  public void overrideRenewalByBarcode(IndividualResource loan, UUID servicePointId) {
    val itemId = loan.getJson().getString("itemId");
    val userId = loan.getJson().getString("userId");

    final Item item = itemsFixture.getById(itemId);
    final User user = usersFixture.getById(userId);

    overrideRenewalByBarcode(OverrideRenewal.builder()
      .itemBarcode(item.getBarcode())
      .userBarcode(user.getBarcode())
      .overrideBlocks(
        new RenewBlockOverrides()
          .withRenewalBlock(
            new RenewBlockOverrideBuilder()
              .create())
          .withComment("Override renewal"))
      .servicePointId(servicePointId.toString())
      .build());
  }
}
