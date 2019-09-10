package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.CoreMatchers.is;

import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public class PatronNoticeMatcher {

  private static final String RECIPIENT_ID = "recipientId";
  private static final String TEMPLATE_ID = "templateId";
  private static final String DELIVERY_CHANNEL = "deliveryChannel";
  private static final String OUTPUT_FORMAT = "outputFormat";
  private static final String CONTEXT = "context";


  public static Matcher<JsonObject> hasEmailNoticeProperties(
    UUID expectedRecipientId, UUID expectedTemplateId,
    Map<String, Matcher<String>> contextMatchersMap) {

    return hasEmailNoticeProperties(expectedRecipientId, expectedTemplateId,
      JsonObjectMatcher.toStringMatcher(contextMatchersMap));
  }

  public static Matcher<JsonObject> hasEmailNoticeProperties(
    UUID expectedRecipientId, UUID expectedTemplateId,
    Matcher<? super String> contextMatcher) {

    return hasNoticeProperties(
      expectedRecipientId, expectedTemplateId,
      "email", "text/html", contextMatcher);
  }

  public static Matcher<JsonObject> hasNoticeProperties(
    UUID expectedRecipientId, UUID expectedTemplateId,
    String expectedDeliveryChannel, String expectedOutputFormat,
    Matcher<? super String> contextMatcher) {

    return JsonObjectMatcher.allOfPaths(
      hasJsonPath(RECIPIENT_ID, UUIDMatcher.is(expectedRecipientId)),
      hasJsonPath(TEMPLATE_ID, UUIDMatcher.is(expectedTemplateId)),
      hasJsonPath(DELIVERY_CHANNEL, is(expectedDeliveryChannel)),
      hasJsonPath(TEMPLATE_ID, UUIDMatcher.is(expectedTemplateId)),
      hasJsonPath(OUTPUT_FORMAT, is(expectedOutputFormat)),
      hasJsonPath(CONTEXT, contextMatcher));
  }


}
