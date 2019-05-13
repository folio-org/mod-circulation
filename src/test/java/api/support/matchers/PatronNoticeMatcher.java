package api.support.matchers;

import java.util.Objects;
import java.util.UUID;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.vertx.core.json.JsonObject;

public class PatronNoticeMatcher extends TypeSafeDiagnosingMatcher<JsonObject> {

  private static final String RECIPIENT_ID = "recipientId";
  private static final String TEMPLATE_ID = "templateId";
  private static final String DELIVERY_CHANNEL = "deliveryChannel";
  private static final String OUTPUT_FORMAT = "outputFormat";

  public static Matcher<JsonObject> equalsToEmailPatronNotice(
    UUID expectedRecipientId, UUID expectedTemplateId) {
    return new PatronNoticeMatcher(expectedRecipientId.toString(), expectedTemplateId.toString(),
      "email", "text/html");
  }

  public static Matcher<JsonObject> equalsToPatronNotice(
    String expectedRecipientId, String expectedTemplateId,
    String expectedDeliveryChannel, String expectedOutputFormat) {
    return new PatronNoticeMatcher(
      expectedRecipientId, expectedTemplateId,
      expectedDeliveryChannel, expectedOutputFormat);
  }

  private String expectedRecipientId;
  private String expectedTemplateId;
  private String expectedDeliveryChannel;
  private String expectedOutputFormat;
  private JsonObject jsonObject;


  private PatronNoticeMatcher(
    String expectedRecipientId, String expectedTemplateId,
    String expectedDeliveryChannel, String expectedOutputFormat) {
    this.expectedRecipientId = expectedRecipientId;
    this.expectedTemplateId = expectedTemplateId;
    this.expectedDeliveryChannel = expectedDeliveryChannel;
    this.expectedOutputFormat = expectedOutputFormat;
  }

  @Override
  protected boolean matchesSafely(JsonObject item, Description mismatchDescription) {

    String recipientId = item.getString(RECIPIENT_ID);
    if (!Objects.equals(expectedRecipientId, recipientId)) {
      mismatchDescription
        .appendText("a notice with a recipient id ")
        .appendValue(recipientId);
      return false;
    }

    String templateId = item.getString(TEMPLATE_ID);
    if (!Objects.equals(expectedTemplateId, templateId)) {
      mismatchDescription
        .appendText("a notice with a template id ")
        .appendValue(recipientId);
      return false;
    }

    String deliveryChannel = item.getString(DELIVERY_CHANNEL);
    if (!Objects.equals(expectedDeliveryChannel, deliveryChannel)) {
      mismatchDescription
        .appendText("a notice with a delivery channel ")
        .appendValue(recipientId);
      return false;
    }


    String outputFormat = item.getString(OUTPUT_FORMAT);
    if (!Objects.equals(expectedOutputFormat, outputFormat)) {
      mismatchDescription
        .appendText("a notice with a output format id ")
        .appendValue(recipientId);
      return false;
    }
    return true;
  }

  @Override
  public void describeTo(Description description) {
    JsonObject expectedNotice = new JsonObject()
      .put(RECIPIENT_ID, expectedRecipientId)
      .put(TEMPLATE_ID, expectedTemplateId)
      .put(DELIVERY_CHANNEL, expectedDeliveryChannel)
      .put(OUTPUT_FORMAT, expectedOutputFormat);

    description.appendText("a notice with body: ")
      .appendValue(expectedNotice.encodePrettily());
  }
}
