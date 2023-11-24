package api.support.fixtures;

import api.support.builders.CirculationItemsBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;

import java.util.UUID;

public class CirculationItemsFixture {
  private final ResourceClient circulationItemsByIdsClient;
  private final ResourceClient circulationItemClient;
  private final MaterialTypesFixture materialTypesFixture;
  private final LoanTypesFixture loanTypesFixture;

  public CirculationItemsFixture(
    MaterialTypesFixture materialTypesFixture,
    LoanTypesFixture loanTypesFixture) {

    circulationItemsByIdsClient = ResourceClient.forCirculationItemsByIds();
    circulationItemClient = ResourceClient.forCirculationItem();
    this.materialTypesFixture = materialTypesFixture;
    this.loanTypesFixture = loanTypesFixture;
  }

  public IndividualResource createCirculationItem(UUID itemId, String barcode, UUID holdingId, UUID locationId) {
    CirculationItemsBuilder circulationItemsBuilder = new CirculationItemsBuilder(itemId, barcode, holdingId, locationId, materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId(), true);
    circulationItemClient.create(circulationItemsBuilder);
    return circulationItemsByIdsClient.create(circulationItemsBuilder);
  }
}
