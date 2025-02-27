package org.folio.circulation.storage.mappers;

import static org.folio.circulation.domain.ItemLossType.AGED_TO_LOST;
import static org.folio.circulation.storage.mappers.ActualCostRecordMapper.toDomain;
import static org.folio.circulation.storage.mappers.ActualCostRecordMapper.toJson;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.Contributor;
import org.folio.circulation.domain.Identifier;
import org.folio.circulation.domain.ItemLossType;
import org.folio.circulation.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ActualCostRecordMapperTest {

  private static final String JSON_EXAMPLE =
    "{ \"id\": \"b4f163a4-ec21-4f92-9dd3-4a6b5f3abb9e\", " +
      "\"status\": \"Open\", " +
      "\"lossType\": \"Aged to lost\", " +
      "\"lossDate\": \"2024-12-09T14:10:01.408+00:00\", " +
      "\"expirationDate\": \"2037-12-09T14:10:01.408+00:00\", " +
      "\"user\": { \"id\": \"15ea95d9-490d-4434-8623-6b51b2252d64\", " +
      "\"barcode\": \"870237949\", " +
      "\"firstName\": \"Kyle\", " +
      "\"lastName\": \"Culpepper\", " +
      "\"middleName\": \"W\", " +
      "\"patronGroupId\": \"8f2661d7-2cd8-4b99-aaf8-fdcf8dd6f4f4\", " +
      "\"patronGroup\": \"Staff, Current\" }, " +
      "\"loan\": { \"id\": \"1096161a-611f-407c-ba27-16a6939733e5\" }, " +
      "\"item\": { \"id\": \"9ed0765a-f940-4dfa-a64c-fc22469ab3a2\", " +
      "\"barcode\": \"88888\", " +
      "\"materialTypeId\": \"1138276a-a784-4874-acbc-a46da0958784\", " +
      "\"materialType\": \"Equipment\", " +
      "\"permanentLocationId\": \"961ae63d-6f43-4ed5-bc34-3c732714c3b2\", " +
      "\"permanentLocation\": \"New Media Center\", " +
      "\"effectiveLocationId\": \"961ae63d-6f43-4ed5-bc34-3c732714c3b2\", " +
      "\"effectiveLocation\": \"New Media Center\", " +
      "\"loanTypeId\": \"564eeff0-653e-4ff0-8efc-231494e08c75\", " +
      "\"loanType\": \"Equipment Digital Renewable\", " +
      "\"holdingsRecordId\": \"70961849-05dd-4a93-a566-edc784fe3583\", " +
      "\"effectiveCallNumberComponents\": { \"callNumber\": \"BAR 01\" }, " +
      "\"volume\": \"IT24560 \", " +
      "\"enumeration\": \"SONY HANDYCAM\", " +
      "\"chronology\": \"SN:1345210556\", " +
      "\"displaySummary\": \"Contains Stuff...\" }, " +
      "\"instance\": { \"id\": \"b1bae4b0-549d-5343-86e0-09437b0dd8b2\", " +
      "\"title\": \"Laptop, Faculty\", " +
      "\"identifiers\": [{ \"value\": \"9915366438302931\", " +
      "\"identifierType\": \"System control number\", " +
      "\"identifierTypeId\": \"7e591197-f335-4afb-bc6d-a6d76ca3bace\" }], " +
      "\"contributors\": [{ \"name\": \"HP\" }] }, " +
      "\"feeFine\": { \"ownerId\": \"3b9a4266-a102-4350-88d5-2048621d0c05\", " +
      "\"owner\": \"New Media Center\", " +
      "\"typeId\": \"73785370-d3bd-4d92-942d-ae2268e02ded\", " +
      "\"type\": \"Lost item fee (actual cost)\" }, " +
      "\"metadata\": { \"createdDate\": \"2024-12-09T14:35:01.515+00:00\", " +
      "\"updatedDate\": \"2024-12-09T14:35:01.515+00:00\" } }";

  private static final JsonObject ACTUAL_COST_RECORD_JSON = new JsonObject(JSON_EXAMPLE);


  @Test
  void testToDomain() throws IOException {
    ActualCostRecord actualCostRecord = ActualCostRecordMapper.toDomain(ACTUAL_COST_RECORD_JSON);

    assertEquals("b4f163a4-ec21-4f92-9dd3-4a6b5f3abb9e", actualCostRecord.getId());
    assertEquals(ActualCostRecord.Status.OPEN, actualCostRecord.getStatus());
    assertEquals(AGED_TO_LOST, actualCostRecord.getLossType());

    assertInstanceOf(ActualCostRecord.ActualCostRecordUser.class, actualCostRecord.getUser());
    assertEquals("15ea95d9-490d-4434-8623-6b51b2252d64", actualCostRecord.getUser().getId());
    assertEquals("870237949", actualCostRecord.getUser().getBarcode());
    assertEquals("Kyle", actualCostRecord.getUser().getFirstName());
    assertEquals("Culpepper", actualCostRecord.getUser().getLastName());
    assertEquals("W", actualCostRecord.getUser().getMiddleName());
    assertEquals("8f2661d7-2cd8-4b99-aaf8-fdcf8dd6f4f4", actualCostRecord.getUser().getPatronGroupId());
    assertEquals("Staff, Current", actualCostRecord.getUser().getPatronGroup());

    // Loan
    assertInstanceOf(ActualCostRecord.ActualCostRecordLoan.class, actualCostRecord.getLoan());
    assertEquals("1096161a-611f-407c-ba27-16a6939733e5", actualCostRecord.getLoan().getId());

    // Item
    assertInstanceOf(ActualCostRecord.ActualCostRecordItem.class, actualCostRecord.getItem());
    assertEquals("9ed0765a-f940-4dfa-a64c-fc22469ab3a2", actualCostRecord.getItem().getId());
    assertEquals("88888", actualCostRecord.getItem().getBarcode());
    assertEquals("1138276a-a784-4874-acbc-a46da0958784", actualCostRecord.getItem().getMaterialTypeId());
    assertEquals("Equipment", actualCostRecord.getItem().getMaterialType());
    assertEquals("961ae63d-6f43-4ed5-bc34-3c732714c3b2", actualCostRecord.getItem().getPermanentLocationId());
    assertEquals("New Media Center", actualCostRecord.getItem().getPermanentLocation());
    assertEquals("New Media Center", actualCostRecord.getItem().getEffectiveLocation());
    assertEquals("564eeff0-653e-4ff0-8efc-231494e08c75", actualCostRecord.getItem().getLoanTypeId());
    assertEquals("Equipment Digital Renewable", actualCostRecord.getItem().getLoanType());
    assertEquals("70961849-05dd-4a93-a566-edc784fe3583", actualCostRecord.getItem().getHoldingsRecordId());
    assertEquals("BAR 01", actualCostRecord.getItem().getEffectiveCallNumberComponents().getCallNumber());
    assertEquals("IT24560 ", actualCostRecord.getItem().getVolume());
    assertEquals("SONY HANDYCAM", actualCostRecord.getItem().getEnumeration());
    assertEquals("SN:1345210556", actualCostRecord.getItem().getChronology());
    assertEquals("Contains Stuff...", actualCostRecord.getItem().getDisplaySummary());

    // Instance
    assertInstanceOf(ActualCostRecord.ActualCostRecordInstance.class, actualCostRecord.getInstance());
    assertEquals("b1bae4b0-549d-5343-86e0-09437b0dd8b2", actualCostRecord.getInstance().getId());
    assertEquals("Laptop, Faculty", actualCostRecord.getInstance().getTitle());
    assertEquals(1, actualCostRecord.getInstance().getIdentifiers().size()); // Null identifiers
    assertEquals(1, actualCostRecord.getInstance().getContributors().size()); // Null contributors

    // FeeFine
    assertInstanceOf(ActualCostRecord.ActualCostRecordFeeFine.class, actualCostRecord.getFeeFine());
    assertNull(actualCostRecord.getFeeFine().getAccountId()); // Null account ID
    assertEquals("New Media Center", actualCostRecord.getFeeFine().getOwner());
    assertEquals("73785370-d3bd-4d92-942d-ae2268e02ded", actualCostRecord.getFeeFine().getTypeId());
    assertEquals("Lost item fee (actual cost)", actualCostRecord.getFeeFine().getType()); // Null type
 }

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

  @Test
  void mappingTest1() {
    JsonObject actualCostRecordJson = new JsonObject()
      .put("id", "b4f163a4-ec21-4f92-9dd3-4a6b5f3abb9e")
      .put("status", "Open")
      .put("lossType", "Aged to lost")
      .put("lossDate", "2024-12-09T14:10:01.408+00:00")
      .put("expirationDate", "2037-12-09T14:10:01.408+00:00")
      .put("user", new JsonObject()
        .put("id", "15ea95d9-490d-4434-8623-6b51b2252d64")
        .put("barcode", "870237949")
        .put("firstName", "Kyle")
        .put("lastName", "Culpepper")
        .put("middleName", "W")
        .put("patronGroupId", null)
        .put("patronGroup", "Staff, Current"))
      .put("loan", new JsonObject()
        .put("id", "1096161a-611f-407c-ba27-16a6939733e5"))
      .put("item", new JsonObject()
        .put("id", "9ed0765a-f940-4dfa-a64c-fc22469ab3a2")
        .put("barcode", "88888")
        .put("materialTypeId", "1138276a-a784-4874-acbc-a46da0958784")
        .put("materialType", "Equipment")
        .put("permanentLocationId", "961ae63d-6f43-4ed5-bc34-3c732714c3b2")
        .put("permanentLocation", "New Media Center")
        .put("effectiveLocationId", "961ae63d-6f43-4ed5-bc34-3c732714c3b2")
        .put("effectiveLocation", "New Media Center")
        .put("loanTypeId", "564eeff0-653e-4ff0-8efc-231494e08c75")
        .put("loanType", "Equipment Digital Renewable")
        .put("holdingsRecordId", "70961849-05dd-4a93-a566-edc784fe3583")
        .put("effectiveCallNumberComponents", new JsonObject()
          .put("callNumber", "BAR 01")
          .put("prefix", "PFX")
          .put("suffix", "SFX"))
        .put("volume", "IT24560 ")
        .put("enumeration", "SONY HANDYCAM")
        .put("chronology", "SN:1345210556")
        .put("displaySummary", "Contains Stuff..."))
      .put("instance", new JsonObject()
        .put("id", "b1bae4b0-549d-5343-86e0-09437b0dd8b2")
        .put("title", "Laptop, Faculty")
        .put("identifiers", new JsonArray()
          .add(new JsonObject()
            .put("value", "9915366438302931")
            .put("identifierType", "System control number")
            .put("identifierTypeId", "7e591197-f335-4afb-bc6d-a6d76ca3bace")))
        .put("contributors", new JsonArray()
          .add(new JsonObject()
            .put("name", "HP"))))
      .put("feeFine", new JsonObject()
        .put("ownerId", "3b9a4266-a102-4350-88d5-2048621d0c05")
        .put("owner", "New Media Center")
        .put("typeId", "73785370-d3bd-4d92-942d-ae2268e02ded")
        .put("type", "Lost item fee (actual cost)"))
      .put("metadata", new JsonObject()
        .put("createdDate", "2024-12-09T14:35:01.515+00:00")
        .put("updatedDate", "2024-12-09T14:35:01.515+00:00"));

    assertEquals(actualCostRecordJson, toJson(toDomain(actualCostRecordJson)));
 }

}
