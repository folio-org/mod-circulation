package api.item;

import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.domain.Item;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.http.ItemResource;

class EffectiveLocationApiTests extends APITests {

  @Test
  void effectiveLocationReturnedWhenPresent() {
    final UUID popularReadingLocationId = UUID.randomUUID();

    ItemResource createdItem = itemsFixture
      .basedUponNod(builder -> builder.withPermanentLocation(popularReadingLocationId));

    final Item item = Item.from(createdItem.getJson())
      .withHoldingsRecord(new HoldingBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create()
      ).withInstance(instanceBuilder().create());

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }

  private InstanceBuilder instanceBuilder() {
    return new InstanceBuilder("", null);
  }
}
