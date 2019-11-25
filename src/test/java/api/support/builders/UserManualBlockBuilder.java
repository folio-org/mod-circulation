package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

import static org.folio.circulation.support.JsonPropertyWriter.write;

public class UserManualBlockBuilder extends JsonBuilder implements Builder {

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

  public UserManualBlockBuilder() {
    this(null, null, null, null,
      null, null, false,
      false, false, null);
  }

  private UserManualBlockBuilder(
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

    write(jsonObject, "id", id);
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

  public UserManualBlockBuilder withId(UUID id) {
    return new UserManualBlockBuilder(
      id,
      this.type,
      this.desc,
      this.staffInformation,
      this.patronMessage,
      this.expirationDate,
      this.borrowing,
      this.renewals,
      this.requests,
      this.userId);
  }

  public UserManualBlockBuilder withType(String type) {
    return new UserManualBlockBuilder(
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

  public UserManualBlockBuilder withDesc(String desc) {
    return new UserManualBlockBuilder(
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

  public UserManualBlockBuilder withStaffInformation(String staffInformation) {
    return new UserManualBlockBuilder(
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

  public UserManualBlockBuilder withPatronMessage(String patronMessage) {
    return new UserManualBlockBuilder(
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

  public UserManualBlockBuilder withExpirationDate(DateTime expirationDate) {
    return new UserManualBlockBuilder(
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

  public UserManualBlockBuilder withBorrowing(boolean borrowing) {
    return new UserManualBlockBuilder(
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

  public UserManualBlockBuilder withRenewals(boolean renewals) {
    return new UserManualBlockBuilder(
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

  public UserManualBlockBuilder withRequests(boolean requests) {
    return new UserManualBlockBuilder(
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

  public UserManualBlockBuilder withUserId(String userId) {
    return new UserManualBlockBuilder(
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
