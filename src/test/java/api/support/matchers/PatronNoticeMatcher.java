package api.support.matchers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.SelfDescribing;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.restassured.path.json.JsonPath;
import io.vertx.core.json.JsonObject;

public class PatronNoticeMatcher extends TypeSafeDiagnosingMatcher<JsonObject> {

  private static final String RECIPIENT_ID = "recipientId";
  private static final String TEMPLATE_ID = "templateId";
  private static final String DELIVERY_CHANNEL = "deliveryChannel";
  private static final String OUTPUT_FORMAT = "outputFormat";
  private static final String CONTEXT = "context";


  public static Matcher<JsonObject> hasEmailNoticeProperties(
    UUID expectedRecipientId, UUID expectedTemplateId,
    Map<String, Matcher<String>> contextMatchers) {

    return hasNoticeProperties(
      expectedRecipientId, expectedTemplateId,
      "email", "text/html", contextMatchers);
  }

  public static Matcher<JsonObject> hasNoticeProperties(
    UUID expectedRecipientId, UUID expectedTemplateId,
    String expectedDeliveryChannel, String expectedOutputFormat,
    Map<String, Matcher<String>> contextMatchers) {

    return new PatronNoticeMatcher(
      expectedRecipientId.toString(), expectedTemplateId.toString(),
      expectedDeliveryChannel, expectedOutputFormat, contextMatchers);
  }

  private String expectedRecipientId;
  private String expectedTemplateId;
  private String expectedDeliveryChannel;
  private String expectedOutputFormat;
  private Map<String, Matcher<String>> contextMatchers;


  private PatronNoticeMatcher(
    String expectedRecipientId, String expectedTemplateId,
    String expectedDeliveryChannel, String expectedOutputFormat,
    Map<String, Matcher<String>> contextMatchers) {
    this.expectedRecipientId = expectedRecipientId;
    this.expectedTemplateId = expectedTemplateId;
    this.expectedDeliveryChannel = expectedDeliveryChannel;
    this.expectedOutputFormat = expectedOutputFormat;
    this.contextMatchers = contextMatchers;
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

    JsonPath context =
      JsonPath.from(item.getJsonObject(CONTEXT).encode());

    Map<String, Matcher<String>> notMatchedKeys = contextMatchers.entrySet().stream()
      .filter(e -> !e.getValue().matches(context.getString(e.getKey())))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (!notMatchedKeys.isEmpty()) {
      List<SelfDescribing> mismatchedKeysDescribing = notMatchedKeys.entrySet().stream()
        .map(e -> getSelfDescribingForContextPathMismatch(e.getKey(), context.get(e.getKey()), e.getValue()))
        .collect(Collectors.toList());
      mismatchDescription.appendText("not matched context paths: ")
        .appendList("<", ", ", ">", mismatchedKeysDescribing);
      mismatchDescription.appendText("> ");
      return false;
    }

    return true;
  }

  private SelfDescribing getSelfDescribingForContextPathMismatch(
    String key, String actual, Matcher<String> matcher) {
    return description -> description
      .appendText(" expected ").appendDescriptionOf(matcher)
      .appendText(" by path ").appendValue(key)
      .appendText(" but was ").appendValue(actual);
  }

  @Override
  public void describeTo(Description description) {
    JsonObject expectedNotice = new JsonObject()
      .put(RECIPIENT_ID, expectedRecipientId)
      .put(TEMPLATE_ID, expectedTemplateId)
      .put(DELIVERY_CHANNEL, expectedDeliveryChannel)
      .put(OUTPUT_FORMAT, expectedOutputFormat);

    description.appendText("a notice with body: ")
      .appendValue(expectedNotice.encode())
      .appendText(" and context containing the following paths: ")
      .appendValue(contextMatchers.keySet());
  }
}
