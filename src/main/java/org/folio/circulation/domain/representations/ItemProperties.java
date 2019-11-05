package org.folio.circulation.domain.representations;

public class ItemProperties {
  private ItemProperties() { }

  public static final String TITLE_PROPERTY = "title";
  public static final String PERMANENT_LOCATION_ID = "permanentLocationId";
  public static final String TEMPORARY_LOCATION_ID = "temporaryLocationId";
  public static final String TEMPORARY_LOAN_TYPE_ID = "temporaryLoanTypeId";
  public static final String PERMANENT_LOAN_TYPE_ID = "permanentLoanTypeId";
  public static final String MATERIAL_TYPE_ID = "materialTypeId";
  public static final String IN_TRANSIT_DESTINATION_SERVICE_POINT_ID = "inTransitDestinationServicePointId";
  public static final String ITEM_CALL_NUMBER_ID = "itemLevelCallNumber";
  public static final String ITEM_CALL_NUMBER_PREFIX_ID = "itemLevelCallNumberPrefix";
  public static final String ITEM_CALL_NUMBER_SUFFIX_ID = "itemLevelCallNumberSuffix";
  public static final String ITEM_COPY_NUMBERS_ID = "copyNumbers";
  public static final String EFFECTIVE_LOCATION_ID = "effectiveLocationId";
}
