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

  public IndividualResource createCirculationItem(String barcode, UUID holdingId, UUID locationId, String instanceTitle) {
    return createCirculationItem(UUID.randomUUID(), barcode, holdingId, locationId, instanceTitle);
  }

  public IndividualResource createCirculationItem(UUID itemId, String barcode, UUID holdingId, UUID locationId, String instanceTitle) {
    CirculationItemsBuilder circulationItemsBuilder = new CirculationItemsBuilder()
      .withItemId(itemId)
      .withBarcode(barcode)
      .withHoldingId(holdingId)
      .withLoanType(loanTypesFixture.canCirculate().getId())
      .withMaterialType(materialTypesFixture.book().getId())
      .withLocationId(locationId)
      .withInstanceTitle(instanceTitle);
    return circulationItemClient.create(circulationItemsBuilder);
  }

  public IndividualResource createCirculationItemForDcb(String barcode, UUID holdingId, UUID locationId,
                                                        String instanceTitle, boolean isDcb) {
    CirculationItemsBuilder circulationItemsBuilder = new CirculationItemsBuilder()
      .withBarcode(barcode)
      .withHoldingId(holdingId)
      .withLoanType(loanTypesFixture.canCirculate().getId())
      .withMaterialType(materialTypesFixture.book().getId())
      .withLocationId(locationId)
      .withInstanceTitle(instanceTitle)
      .withDcb(isDcb);

    return circulationItemClient.create(circulationItemsBuilder);
  }

  public IndividualResource createCirculationItemWithLendingLibrary(String barcode, UUID holdingId, UUID locationId, String lendingLibrary) {
    CirculationItemsBuilder circulationItemsBuilder = new CirculationItemsBuilder().withBarcode(barcode).withHoldingId(holdingId)
      .withLoanType(loanTypesFixture.canCirculate().getId()).withMaterialType(materialTypesFixture.book().getId())
      .withLocationId(locationId).withLendingLibraryCode(lendingLibrary);
    return circulationItemClient.create(circulationItemsBuilder);
  }
}
