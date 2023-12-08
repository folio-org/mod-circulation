package api.support.fixtures;

import api.support.builders.CirculationItemsBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;

import java.util.UUID;

public class CirculationItemsFixture {
  private final ResourceClient circulationItemsClient;
  private final ResourceClient circulationItemClient;
  private final MaterialTypesFixture materialTypesFixture;
  private final LoanTypesFixture loanTypesFixture;

  public CirculationItemsFixture(
    MaterialTypesFixture materialTypesFixture,
    LoanTypesFixture loanTypesFixture) {

    circulationItemsClient = ResourceClient.forCirculationItems();
    circulationItemClient = ResourceClient.forCirculationItem();
    this.materialTypesFixture = materialTypesFixture;
    this.loanTypesFixture = loanTypesFixture;
  }

  public IndividualResource createCirculationItem(String barcode, UUID holdingId, UUID locationId) {
    CirculationItemsBuilder circulationItemsBuilder = new CirculationItemsBuilder().withBarcode(barcode).withHoldingId(holdingId)
      .withLoanType(loanTypesFixture.canCirculate().getId()).withMaterialType(materialTypesFixture.book().getId())
      .withLocationId(locationId);
    circulationItemClient.create(circulationItemsBuilder);
    return circulationItemsClient.create(circulationItemsBuilder);
  }
}
