package api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.testcontainers.Testcontainers.exposeHostPorts;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import io.restassured.RestAssured;

/**
 * Integration test executed in the "mvn verify" phase.
 *
 * <p>Check the shaded fat uber jar (this is not tested in "mvn test" unit tests).
 *
 * <p>Check the Dockerfile (this is not tested in "mvn test" unit tests).
 *
 * <p>The Drools engine used in mod-circulation worked in unit test
 * but failed when running the jar in the container several times:
 * <a href="https://issues.folio.org/browse/CIRC-309">CIRC-309</a>,
 * <a href="https://issues.folio.org/browse/CIRC-1147">CIRC-1147</a>,
 * <a href="https://issues.folio.org/browse/CIRC-1676">CIRC-1676</a>
 *
 * <p>Note: Integration tests don't contribute to Sonar code coverage.
 */
@Testcontainers
class DroolsIT {

  /** testcontainers logging requires log4j-slf4j-impl in test scope */
  private static final Logger LOG = LoggerFactory.getLogger(DroolsIT.class);
  private static String OKAPI_URL;

  @RegisterExtension
  private static WireMockExtension OKAPI = WireMockExtension.newInstance()
  .options(WireMockConfiguration.wireMockConfig().dynamicPort()
      .extensions(new ResponseTemplateTransformer(true))).build();

  @Container
  private static final GenericContainer<?> MOD_CIRCULATION =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-circulation").withFileFromPath(".", Path.of(".")))
    .withExposedPorts(9801)
    .withAccessToHost(true);

  @BeforeAll
  static void beforeAll() {
    OKAPI_URL = "http://host.testcontainers.internal:" + OKAPI.getPort();
    exposeHostPorts(OKAPI.getPort());
    RestAssured.baseURI = "http://" + MOD_CIRCULATION.getHost() + ":" + MOD_CIRCULATION.getFirstMappedPort();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    MOD_CIRCULATION.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams());
  }

  @Test
  void health() {
    when().
      get("/admin/health").
    then().
      statusCode(200);
  }

  @Test
  void checkOutByBarcode() {
    stub("/users\\?.*", "{'users':[{'id':'1060a4c9-e7e1-435f-a90c-a47f96b3d9e2', "
        + "'patronGroup':'1c9bb80e-b2f0-451c-85b8-f6fc22d65378', 'active':true}]}");
    stub("/groups\\?.*", "{'usergroups':[{'group':'staff'}], 'totalRecords':1}");
    stub("/manualblocks\\?.*", "{'manualblocks':[]}");
    stub("/automated-patron-blocks/.*", "{'automatedPatronBlocks':[]}");
    stub("/item-storage/items\\?.*", "{'items':[{"
        + "'id':'cd738da1-5b28-4d1b-99b4-31420b7d80ab',"
        + "'holdingsRecordId':'b96ef84b-e8b2-47fb-a40d-12a440bc99ed',"
        + "'effectiveLocationId':'24be82a3-f7f0-490d-9598-473faa0f47ac',"
        + "'status':{'name':'Available'},"
        + "'materialTypeId':'3d687cf8-10e4-45c8-8433-7b1208619f7a',"
        + "'permanentLoanTypeId':'40d1e75c-829d-4da5-807b-3a1fbc041831'"
        + "}]}");
    stub("/holdings-storage/holdings/.*", "{'instanceId':'a02b6833-1fd6-441a-bfda-5249d2d56f66'}");
    stub("/instance-storage/instances/.*", "{}");
    stub("/loan-storage/loans\\?.*", "{'loans':[]}");
    stub("/configurations/entries\\?.*", "{'configs':[]}");
    stub("/request-storage/requests\\?.*", "{'requests':[]}");
    stub("/locations/.*", "{'id':'24be82a3-f7f0-490d-9598-473faa0f47ac', "
        + "'primaryServicePoint':'54ca624a-d37f-4e16-b274-7284ce2ecbd8'}");
    stub("/service-points/.*", "{}");
    stub("/material-types/.*", "{'id':'3d687cf8-10e4-45c8-8433-7b1208619f7a', 'name':'book'}");
    stub("/loan-types/.*", "{'id':'40d1e75c-829d-4da5-807b-3a1fbc041831'}");
    stub("/circulation-rules-storage",
        "{'rulesAsText':'priority: number-of-criteria, criterium (t, s, c, b, a, m, g), last-line\\n"
        + "fallback-policy: l 6a538356-aa91-44b6-89ab-45b9d665097c r 0462da3a-a106-46d1-9682-a4d7eeada8b3 "
        + "n 0772d7eb-0bde-4e8d-a17e-51ca8e6aabcc o 02f06cd0-76ed-4b7e-9e3c-820a61e9a115 "
        + "i 01e2ccd7-8a5a-4cfa-80a4-7ea6f1c5aa10'}");
    stub("/loan-policy-storage/loan-policies/.*", "{'id':'6a538356-aa91-44b6-89ab-45b9d665097c',"
        + "'name':'Rolling 4 Weeks', 'loanable':true, "
        + "'loansPolicy':{'profileId':'Rolling', 'closedLibraryDueDateManagementId': 'CURRENT_DUE_DATE', "
        + "'period':{'duration':4, 'intervalId':'Weeks'}}}");
    stub("/overdue-fines-policies/.*", "{'name':'default overdue fine'}");
    stub("/lost-item-fees-policies/.*", "{'name':'default lost fee'}");
    stub("/patron-notice-policy-storage/patron-notice-policies/.*", "{}");
    stub("/calendar/dates/54ca624a-d37f-4e16-b274-7284ce2ecbd8/surrounding-openings\\?.*",
        "{'openings':[{},{},{}]}");
    stubFor(put("/item-storage/items/cd738da1-5b28-4d1b-99b4-31420b7d80ab").willReturn(noContent()));
    stubFor(post("/loan-storage/loans").willReturn(aResponse().withStatus(201).withBody("{{{request.body}}}")));
    stubFor(post("/pubsub/publish").willReturn(noContent()));
    stubFor(post("/patron-action-session-storage/patron-action-sessions")
        .willReturn(aResponse().withStatus(201).withBody("{{{request.body}}}")));
    stub("/settings/entries.*", "{}");
    given().
      header("X-Okapi-Url", OKAPI_URL).
      header("X-Okapi-Tenant", "diku").
      body("{'userBarcode':'123', 'itemBarcode':'789', 'servicePointId':'54ca624a-d37f-4e16-b274-7284ce2ecbd8'}"
          .replace('\'', '"')).
    when().
      post("/circulation/check-out-by-barcode").
    then().
      statusCode(201);
  }

  /**
   * WireMock stub for a GET request returning 200 and a JSON body.
   *
   * @param path URL regexp
   * @param json JSON body to return; for Java file readability it replaces each single quote ' with a double quote "
   */
  private static void stub(String path, String json) {
    OKAPI.stubFor(get(urlMatching(path)).willReturn(okJson(json.replace('\'', '"'))));
  }

  private static StubMapping stubFor(MappingBuilder mappingBuilder) {
    return OKAPI.stubFor(mappingBuilder);
  }
}
