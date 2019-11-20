package api.item;

import static api.support.matchers.UUIDMatcher.is;
import static org.junit.Assert.assertThat;

import java.util.UUID;

import org.folio.circulation.domain.Item;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.http.InventoryItemResource;

public class EffectiveLocationApiTests extends APITests {

  @Test
  public void effectiveLocationReturnedWhenPresent() throws Exception {
    final UUID popularReadingLocationId = UUID.randomUUID();

    InventoryItemResource createdItem = itemsFixture
      .basedUponNod(builder -> builder.withPermanentLocation(popularReadingLocationId));

    final Item item = new Item(
      createdItem.getJson(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
        instanceBuilder().create(),
        null, null, null, null, null);

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }

  private InstanceBuilder instanceBuilder() {
    return new InstanceBuilder("", null);
  }
}
