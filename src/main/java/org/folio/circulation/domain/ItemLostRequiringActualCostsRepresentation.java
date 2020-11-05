package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class ItemLostRequiringActualCostsRepresentation {
  private static final String TITLE_KEY = "title";
  private static final String LOAN_KEY = "loan";
  private static final String STATUS_KEY = "status";
  private static final String LOAN_DATE_KEY = "loanDate";
  private static final String DUE_DATE_KEY = "dueDate";
  private static final String PATRON_KEY = "patron";
  private static final String INSTANCE_KEY = "instance";
  private static final String PERMANENT_LOCATION_KEY = "permanentLocation";
  private static final String MATERIAL_TYPE_KEY = "materialType";
  private static final String FEE_FINE_OWNER_KEY = "feeFineOwner";
  private static final String ID_KEY = "id";
  private static final String NAME_KEY = "name";
  private static final String LIBRARY_NAME_KEY = "libraryName";
  private static final String CODE_KEY = "code";
  private static final String LAST_NAME_KEY = "lastName";
  private static final String MIDDLE_NAME_KEY = "middleName";
  private static final String FIRST_NAME_KEY = "firstName";
  private static final String OWNER_KEY = "owner";

  private ItemLostRequiringActualCostsRepresentation() {}

  public static JsonObject mapToResult(LostItemRequiringCostsFee entry) {
    if (entry == null) {
      return null;
    }

    final JsonObject representation = new JsonObject();
    final Item item = entry.getItem();
    final Loan loan = entry.getLoan();
    final FeeFineOwner feeFineOwner = entry.getFeeFineOwner();

    if (item != null) {
      final JsonObject statusRepresentation = new JsonObject();

      statusRepresentation.put(NAME_KEY, item.getStatusName());

      representation.put(ID_KEY, item.getItemId());
      representation.put(TITLE_KEY, item.getTitle());
      representation.put(STATUS_KEY, statusRepresentation);

      if (item.getInstanceId() != null) {
        final JsonObject instanceRepresentation = new JsonObject();

        instanceRepresentation.put(ID_KEY, item.getInstanceId());

        representation.put(INSTANCE_KEY, instanceRepresentation);
      }

      if (item.getPermanentLocation() != null) {
        final JsonObject locationRepresentation = new JsonObject();
        final Location location = item.getPermanentLocation();

        locationRepresentation.put(ID_KEY, location.getId());
        locationRepresentation.put(NAME_KEY, location.getName());
        locationRepresentation.put(LIBRARY_NAME_KEY, location.getLibraryName());
        locationRepresentation.put(CODE_KEY, location.getCode());

        representation.put(PERMANENT_LOCATION_KEY, locationRepresentation);
      }

      if (item.getMaterialType() != null) {
        representation.put(MATERIAL_TYPE_KEY, item.getMaterialType());
      }
    }

    if (loan != null) {
      final JsonObject loanRepresentation = new JsonObject();

      loanRepresentation.put(ID_KEY, loan.getId());
      loanRepresentation.put(LOAN_DATE_KEY, loan.getLoanDate().toString());
      loanRepresentation.put(DUE_DATE_KEY, loan.getDueDate().toString());

      representation.put(LOAN_KEY, loanRepresentation);

      if (loan.getUser() != null) {
        final User patron = loan.getUser();
        final JsonObject patronRepresentation = new JsonObject();

        patronRepresentation.put(ID_KEY, patron.getId());
        patronRepresentation.put(LAST_NAME_KEY, patron.getLastName());
        patronRepresentation.put(MIDDLE_NAME_KEY, patron.getMiddleName());
        patronRepresentation.put(FIRST_NAME_KEY, patron.getFirstName());

        representation.put(PATRON_KEY, patronRepresentation);
      }
    }

    if (feeFineOwner != null) {
      final JsonObject feeFineOwnerRepresentation = new JsonObject();

      feeFineOwnerRepresentation.put(ID_KEY, feeFineOwner.getId());
      feeFineOwnerRepresentation.put(OWNER_KEY, feeFineOwner.getOwner());

      representation.put(FEE_FINE_OWNER_KEY, feeFineOwnerRepresentation);
    }

    return representation;
  }
}
