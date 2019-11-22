package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

import static org.folio.circulation.support.JsonPropertyWriter.write;

public class ManualBlockBuilder extends JsonBuilder implements Builder {

  private final UUID id;
  private final String type;
  private final String desc;
  private final String staffInformation;
  private final String patronMessage;
  private final DateTime expirationDate;
  private final boolean borrowing;
  private final boolean renewals;
  private final boolean requests;
  private final String userId;

  public ManualBlockBuilder() {
    this(null, null, null, null,
      null, null, false,
      false, false, null);
  }

  private ManualBlockBuilder(
    UUID id,
    String type,
    String desc,
    String staffInformation,
    String patronMessage,
    DateTime expirationDate,
    boolean borrowing,
    boolean renewals,
    boolean requests,
    String userId) {
    this.id = id;
    this.type = type;
    this.desc = desc;
    this.staffInformation = staffInformation;
    this.patronMessage = patronMessage;
    this.expirationDate = expirationDate;
    this.borrowing = borrowing;
    this.renewals = renewals;
    this.requests = requests;
    this.userId = userId;
  }

  @Override
  public JsonObject create() {
    final JsonObject jsonObject = new JsonObject();

    write(jsonObject, "type", type);
    write(jsonObject, "desc",  desc);
    write(jsonObject, "staffInformation", staffInformation);
    write(jsonObject, "patronMessage", patronMessage);
    write(jsonObject, "expirationDate", expirationDate);
    write(jsonObject, "borrowing", borrowing);
    write(jsonObject, "renewals", renewals);
    write(jsonObject, "requests", requests);
    write(jsonObject, "userId", userId);

    return jsonObject;
  }

  public ManualBlockBuilder withType(String type) {
    return new ManualBlockBuilder(
      this.id,
      type,
      this.desc,
      this.staffInformation,
      this.patronMessage,
      this.expirationDate,
      this.borrowing,
      this.renewals,
      this.requests,
      this.userId);
  }

  public ManualBlockBuilder withDesc(String desc) {
    return new ManualBlockBuilder(
      this.id,
      this.type,
      desc,
      this.staffInformation,
      this.patronMessage,
      this.expirationDate,
      this.borrowing,
      this.renewals,
      this.requests,
      this.userId);
  }

  public ManualBlockBuilder withStaffInformation(String staffInformation) {
    return new ManualBlockBuilder(
      this.id,
      this.type,
      this.desc,
      staffInformation,
      this.patronMessage,
      this.expirationDate,
      this.borrowing,
      this.renewals,
      this.requests,
      this.userId);
  }

  public ManualBlockBuilder withPatronMessage(String patronMessage) {
    return new ManualBlockBuilder(
      this.id,
      this.type,
      this.desc,
      this.staffInformation,
      patronMessage,
      this.expirationDate,
      this.borrowing,
      this.renewals,
      this.requests,
      this.userId);
  }

  public ManualBlockBuilder withExpirationDate(DateTime expirationDate) {
    return new ManualBlockBuilder(
      this.id,
      this.type,
      this.desc,
      this.staffInformation,
      this.patronMessage,
      expirationDate,
      this.borrowing,
      this.renewals,
      this.requests,
      this.userId);
  }

  public ManualBlockBuilder withBorrowing(boolean borrowing) {
    return new ManualBlockBuilder(
      this.id,
      this.type,
      this.desc,
      this.staffInformation,
      this.patronMessage,
      this.expirationDate,
      borrowing,
      this.renewals,
      this.requests,
      this.userId);
  }

  public ManualBlockBuilder withRenewals(boolean renewals) {
    return new ManualBlockBuilder(
      this.id,
      this.type,
      this.desc,
      this.staffInformation,
      this.patronMessage,
      this.expirationDate,
      this.borrowing,
      renewals,
      this.requests,
      this.userId);
  }

  public ManualBlockBuilder withRequests(boolean requests) {
    return new ManualBlockBuilder(
      this.id,
      this.type,
      this.desc,
      this.staffInformation,
      this.patronMessage,
      this.expirationDate,
      this.borrowing,
      this.renewals,
      requests,
      this.userId);
  }

  public ManualBlockBuilder withUserId(String userId) {
    return new ManualBlockBuilder(
      this.id,
      this.type,
      this.desc,
      this.staffInformation,
      this.patronMessage,
      this.expirationDate,
      this.borrowing,
      this.renewals,
      this.requests,
      userId);
  }
}
