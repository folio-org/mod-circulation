package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.Collection;

import org.folio.circulation.domain.Contributor;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MaterialType;

import io.vertx.core.json.JsonObject;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CirculationItemMapper {
  private static final String ID = "id";
  private static final String BARCODE = "barcode";
  private static final String HOLDINGS_RECORD_ID = "holdingsRecordId";
  private static final String ENUMERATION = "enumeration";
  private static final String VOLUME = "volume";
  private static final String CHRONOLOGY = "chronology";
  private static final String COPY_NUMBER = "copyNumber";
  private static final String ITEM_LEVEL_COPY_NUMBER = "itemLevelCopyNumber";
  private static final String HOLDINGS_LEVEL_COPY_NUMBER = "holdingsLevelCopyNumber";
  private static final String YEAR_CAPTION = "yearCaption";
  private static final String CALL_NUMBER = "callNumber";
  private static final String CALL_NUMBER_PREFIX = "callNumberPrefix";
  private static final String CALL_NUMBER_SUFFIX = "callNumberSuffix";
  private static final String PREFIX = "prefix";
  private static final String SUFFIX = "suffix";
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS = "effectiveCallNumberComponents";
  private static final String EFFECTIVE_LOCATION_ID = "effectiveLocationId";
  private static final String PERMANENT_LOAN_TYPE_ID = "permanentLoanTypeId";
  private static final String TEMPORARY_LOAN_TYPE_ID = "temporaryLoanTypeId";
  private static final String MATERIAL_TYPE_NAME = "materialTypeName";
  private static final String MATERIAL_TYPE_ID = "materialTypeId";
  private static final String INSTANCE_ID = "instanceId";
  private static final String TITLE = "title";
  private static final String CONTRIBUTORS = "contributors";
  private static final String EFFECTIVE_LOCATION_NAME = "effectiveLocationName";
  private static final String INSTITUTION_ID = "institutionId";
  private static final String CAMPUS_ID = "campusId";
  private static final String LIBRARY_ID = "libraryId";
  private static final String NAME = "name";
  private static final String STATUS = "status";

  public static Item toItem(JsonObject circulationItem) {
    final JsonObject item = new JsonObject();
    write(item, ID, circulationItem.getString(ID));
    write(item, BARCODE, circulationItem.getString(BARCODE));
    write(item, HOLDINGS_RECORD_ID, circulationItem.getString(HOLDINGS_RECORD_ID));
    write(item, ENUMERATION, circulationItem.getString(ENUMERATION));
    write(item, VOLUME, circulationItem.getString(VOLUME));
    write(item, CHRONOLOGY, circulationItem.getString(CHRONOLOGY));
    write(item, COPY_NUMBER, circulationItem.getString(ITEM_LEVEL_COPY_NUMBER));
    write(item, YEAR_CAPTION, circulationItem.getJsonArray(YEAR_CAPTION));
    write(item, PERMANENT_LOAN_TYPE_ID, circulationItem.getString(PERMANENT_LOAN_TYPE_ID));
    write(item, TEMPORARY_LOAN_TYPE_ID, circulationItem.getString(TEMPORARY_LOAN_TYPE_ID));
    write(item, MATERIAL_TYPE_ID, circulationItem.getString(MATERIAL_TYPE_ID));
    write(item, EFFECTIVE_LOCATION_ID, circulationItem.getString(EFFECTIVE_LOCATION_ID));

    final JsonObject status = new JsonObject();
    write(status, NAME, circulationItem.getString(STATUS));
    write(item, STATUS, status);

    final JsonObject callNumberComponents = new JsonObject();
    write(callNumberComponents, CALL_NUMBER, circulationItem.getString(CALL_NUMBER));
    write(callNumberComponents, PREFIX, circulationItem.getString(CALL_NUMBER_PREFIX));
    write(callNumberComponents, SUFFIX, circulationItem.getString(CALL_NUMBER_SUFFIX));
    write(item, EFFECTIVE_CALL_NUMBER_COMPONENTS, callNumberComponents);

    final JsonObject location = new JsonObject();
    write(location, ID,  circulationItem.getString(EFFECTIVE_LOCATION_ID));
    write(location, NAME, circulationItem.getString(EFFECTIVE_LOCATION_NAME));
    write(location, INSTITUTION_ID, circulationItem.getString(INSTITUTION_ID));
    write(location, CAMPUS_ID, circulationItem.getString(CAMPUS_ID));
    write(location, LIBRARY_ID, circulationItem.getString(LIBRARY_ID));

    Holdings holdings = new Holdings(circulationItem.getString(INSTANCE_ID),
      circulationItem.getString(HOLDINGS_LEVEL_COPY_NUMBER), null);

    Instance instance = new Instance(circulationItem.getString(TITLE), emptyList(),
      getContributors(circulationItem));

    MaterialType materialType = new MaterialType(circulationItem.getString(MATERIAL_TYPE_NAME), null);

    return Item.from(item)
      .withHoldings(holdings)
      .withInstance(instance)
      .withMaterialType(materialType)
      .withLocation(Location.from(location));
  }

  private static Collection<Contributor> getContributors(JsonObject circulationItem) {
    return circulationItem.getJsonArray(CONTRIBUTORS)
      .stream()
      .filter(String.class::isInstance)
      .map(String.class::cast)
      .map(name -> new Contributor(name, null))
      .collect(toList());
  }

}
