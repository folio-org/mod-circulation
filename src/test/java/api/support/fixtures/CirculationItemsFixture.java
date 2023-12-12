package api.support.fixtures;

import api.support.builders.CirculationItemsBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;

import java.util.UUID;

public class CirculationItemsFixture {
  private final ResourceClient circulationItemClient;
  private final MaterialTypesFixture materialTypesFixture;
  private final LoanTypesFixture loanTypesFixture;

  public CirculationItemsFixture(
    MaterialTypesFixture materialTypesFixture,
    LoanTypesFixture loanTypesFixture) {

    circulationItemClient = ResourceClient.forCirculationItem();
    this.materialTypesFixture = materialTypesFixture;
    this.loanTypesFixture = loanTypesFixture;
  }

  public IndividualResource createCirculationItem(String barcode, UUID holdingId, UUID locationId) {
    CirculationItemsBuilder circulationItemsBuilder = new CirculationItemsBuilder().withBarcode(barcode).withHoldingId(holdingId)
      .withLoanType(loanTypesFixture.canCirculate().getId()).withMaterialType(materialTypesFixture.book().getId())
      .withLocationId(locationId);
    return circulationItemClient.create(circulationItemsBuilder);
  }

  public IndividualResource createCirculationItemWithLandingLibrary(String barcode, UUID holdingId, UUID locationId, String landingLibrary) {
    CirculationItemsBuilder circulationItemsBuilder = new CirculationItemsBuilder().withBarcode(barcode).withHoldingId(holdingId)
      .withLoanType(loanTypesFixture.canCirculate().getId()).withMaterialType(materialTypesFixture.book().getId())
      .withLocationId(locationId).withLendingLibraryCode(landingLibrary);
    return circulationItemClient.create(circulationItemsBuilder);
  }
}
