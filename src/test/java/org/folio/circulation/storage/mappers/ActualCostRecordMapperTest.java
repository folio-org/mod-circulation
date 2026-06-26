package org.folio.circulation.storage.mappers;

import static org.folio.circulation.storage.mappers.ActualCostRecordMapper.toDomain;
import static org.folio.circulation.storage.mappers.ActualCostRecordMapper.toJson;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ActualCostRecordMapperTest {

  @Test
  void mappingTest() {
    JsonObject actualCostRecordJson = new JsonObject()
      .put("id", "c98215a8-987a-4ccd-9f3e-17b2f468d487")
      .put("lossType", "Declared lost")
      .put("lossDate", "2023-10-30T11:22:23.053Z")
      .put("expirationDate", "2023-10-30T11:22:23.053Z")
      .put("status", "Billed")
      .put("user", new JsonObject()
        .put("id", "72235d24-aead-4647-bad2-2febb12d940a")
        .put("barcode", "1")
        .put("firstName", "First")
        .put("lastName", "Last")
        .put("middleName", "Middle")
        .put("patronGroupId", "503a81cd-6c26-400f-b620-14c08943697c")
        .put("patronGroup", "Faculty"))
      .put("loan", new JsonObject()
        .put("id", "1e65b451-3cde-4575-93c1-9b7ff03db4fd"))
      .put("item", new JsonObject()
        .put("id", "bbe89d07-791a-48c5-86b1-79441f5fcd49")
        .put("barcode", "abc123")
        .put("materialTypeId", "d9acad2f-2aac-4b48-9097-e6ab85906b25")
        .put("materialType", "text")
        .put("permanentLocationId", "fcd64ce1-6995-48f0-840e-89ffa2288371")
        .put("permanentLocation", "Main Library")
        .put("effectiveLocationId", "a2a89dec-522b-4c1d-9690-a2f922869e68")
        .put("effectiveLocation", "Annex")
        .put("loanTypeId", "2b94c631-fca9-4892-a730-03ee529ffe27")
        .put("loanType", "Can circulate")
        .put("holdingsRecordId", "e6d7e91a-4dbc-4a70-9b38-e000d2fbdc79")
        .put("volume", "vol")
        .put("enumeration", "enum")
        .put("chronology", "chrono")
        .put("copyNumber", "copy")
        .put("effectiveCallNumberComponents", new JsonObject()
          .put("callNumber", "CN")
          .put("prefix", "PFX")
          .put("suffix", "SFX")))
      .put("instance", new JsonObject()
        .put("id", "cf23adf0-61ba-4887-bf82-956c4aae2260")
        .put("title", "Test book")
        .put("identifiers", new JsonArray()
          .add(new JsonObject()
            .put("value", "1447294130")
            .put("identifierTypeId", "8261054f-be78-422d-bd51-4ed9f33c3422")
            .put("identifierType", "ISBN")))
        .put("contributors", new JsonArray()
          .add(new JsonObject()
            .put("name", "Test, Author"))))
      .put("feeFine", new JsonObject()
        .put("accountId", "cdac704f-17aa-57fa-9500-607c5fa792bd")
        .put("ownerId", "94aecb81-b025-4eca-9e0e-14756d052836")
        .put("owner", "Test owner")
        .put("typeId", "73785370-d3bd-4d92-942d-ae2268e02ded")
        .put("type", "Lost item fee (actual cost)"));

    assertEquals(actualCostRecordJson, toJson(toDomain(actualCostRecordJson)));
  }

}
