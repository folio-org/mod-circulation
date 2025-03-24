package org.folio.circulation.domain.mapper;

import static java.util.stream.Collectors.joining;

import java.util.Locale;
import java.util.stream.Stream;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Department;
import org.folio.circulation.domain.User;

public class UserMapper {
  private static final Logger log = LogManager.getLogger(UserMapper.class);

  private static final String FIRST_NAME = "firstName";
  private static final String PREFERRED_FIRST_NAME = "preferredFirstName";
  private static final String LAST_NAME = "lastName";
  private static final String MIDDLE_NAME = "middleName";
  private static final String BARCODE = "barcode";
  private static final String PATRON_GROUP = "patronGroup";
  private static final String DEPARTMENTS = "departments";

  private static final String PRIMARY_ADDRESS_ADDRESS_TYPE_NAME = "primaryDeliveryAddressType";
  private static final String PRIMARY_ADDRESS_ADDRESS_LINE_1 = "primaryAddressLine1";
  private static final String PRIMARY_ADDRESS_ADDRESS_LINE_2 = "primaryAddressLine2";
  private static final String PRIMARY_ADDRESS_CITY = "primaryCity";
  private static final String PRIMARY_ADDRESS_REGION = "primaryStateProvRegion";
  private static final String PRIMARY_ADDRESS_POSTAL_CODE = "primaryZipPostalCode";
  private static final String PRIMARY_ADDRESS_COUNTRY_ID = "primaryCountry";

  private static final String ADDRESS_TYPE_NAME = "addressType";
  private static final String ADDRESS_LINE_1 = "addressLine1";
  private static final String ADDRESS_LINE_2 = "addressLine2";
  private static final String CITY = "city";
  private static final String REGION = "region";
  private static final String POSTAL_CODE = "postalCode";
  private static final String COUNTRY_ID = "countryId";

  private static final String PROP_ADDRESS_LINE_1 = "addressLine1";
  private static final String PROP_ADDRESS_LINE_2 = "addressLine2";
  private static final String PROP_CITY = "city";
  private static final String PROP_REGION = "region";
  private static final String PROP_POSTAL_CODE = "postalCode";
  private static final String PROP_COUNTRY_ID = "countryId";
  private static final String PROP_ADDRESS_TYPE_NAME = "addressTypeName";

  private UserMapper() {
  }

  public static JsonObject createUserContext(User user, String deliveryAddressTypeId) {
    JsonObject userContext = createUserContext(user);
    var address = user.getAddressByType(deliveryAddressTypeId);
    if (address != null) {
      userContext = userContext
        .put(ADDRESS_LINE_1, address.getString(PROP_ADDRESS_LINE_1, null))
        .put(ADDRESS_LINE_2, address.getString(PROP_ADDRESS_LINE_2, null))
        .put(CITY, address.getString(PROP_CITY, null))
        .put(REGION, address.getString(PROP_REGION, null))
        .put(POSTAL_CODE, address.getString(PROP_POSTAL_CODE, null))
        .put(COUNTRY_ID, address.getString(PROP_COUNTRY_ID, null))
        .put(ADDRESS_TYPE_NAME, address.getString(PROP_ADDRESS_TYPE_NAME, null));
    }
    return userContext;
  }

  public static JsonObject createUserContext(User user) {
    JsonObject userContext = new JsonObject()
      .put(FIRST_NAME, user.getFirstName())
      .put(PREFERRED_FIRST_NAME, getPreferredFirstName(user))
      .put(LAST_NAME, user.getLastName())
      .put(MIDDLE_NAME, user.getMiddleName())
      .put(BARCODE, user.getBarcode())
      .put(PATRON_GROUP, user.getPatronGroup() != null ? user.getPatronGroup().getGroup() : "")
      .put(DEPARTMENTS, user.getDepartments() != null && !user.getDepartments().isEmpty()
        ? user.getDepartments().stream().map(Department::getName).collect(joining("; "))
        : "");

    var primaryAddress = user.getPrimaryAddress();
    if (primaryAddress != null) {
      userContext = userContext
        .put(PRIMARY_ADDRESS_ADDRESS_LINE_1, primaryAddress.getString(PROP_ADDRESS_LINE_1, null))
        .put(PRIMARY_ADDRESS_ADDRESS_LINE_2, primaryAddress.getString(PROP_ADDRESS_LINE_2, null))
        .put(PRIMARY_ADDRESS_CITY, primaryAddress.getString(PROP_CITY, null))
        .put(PRIMARY_ADDRESS_REGION, primaryAddress.getString(PROP_REGION, null))
        .put(PRIMARY_ADDRESS_POSTAL_CODE, primaryAddress.getString(PROP_POSTAL_CODE, null))
        .put(PRIMARY_ADDRESS_COUNTRY_ID, getCountryNameByCode(primaryAddress.getString(PROP_COUNTRY_ID, null)))
        .put(PRIMARY_ADDRESS_ADDRESS_TYPE_NAME, primaryAddress.getString(PROP_ADDRESS_TYPE_NAME, null));
    }
    return userContext;
  }

  private static String getPreferredFirstName(User user) {
    return user.getPreferredFirstName() == null
      ? user.getFirstName()
      : user.getPreferredFirstName();
  }

  private static String getCountryNameByCode(String code) {
    if (StringUtils.isEmpty(code) || !Stream.of(Locale.getISOCountries()).toList().contains(code)) {
      log.info("getCountryNameByCode:: Invalid country code {}", code);
      return null;
    }
    return Locale.of("",code).getDisplayName();
  }
}
