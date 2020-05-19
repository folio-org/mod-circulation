package org.folio.circulation.services;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.services.support.RefundAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import io.vertx.core.json.JsonObject;

@RunWith(MockitoJUnitRunner.class)
public class FeeFineServiceTest {
  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient accountClient;
  @Mock
  private CollectionResourceClient accountActionsClient;
  @Mock
  private CollectionResourceClient userClient;
  @Mock
  private CollectionResourceClient servicePointClient;
  private FeeFineService feeFineService;

  @Before
  public void setUp() {
    when(clients.accountsStorageClient()).thenReturn(accountClient);
    when(clients.feeFineActionsStorageClient()).thenReturn(accountActionsClient);
    when(clients.usersStorage()).thenReturn(userClient);
    when(clients.servicePointsStorage()).thenReturn(servicePointClient);

    feeFineService = new FeeFineService(clients);

    when(userClient.get(anyString()))
      .thenReturn(completedFuture(succeeded(emptyJsonResponse(200))));
    when(servicePointClient.get(anyString()))
      .thenReturn(completedFuture(succeeded(emptyJsonResponse(200))));
  }

  @Test
  public void shouldForwardFailureIfAnAccountIsNotCreated() {
    final String expectedError = "Fee fine account failed to be created";

    when(accountClient.post(any())).thenAnswer(postRespondWithRequestAndFail());

    when(accountActionsClient.post(any(JsonObject.class)))
      .thenReturn(completedFuture(succeeded(emptyJsonResponse(201))));

    final Result<Void> result = feeFineService.createAccounts(Arrays.asList(
      createCommandBuilder().build(),
      createCommandBuilder().build()))
      .getNow(null);

    assertThat(result, notNullValue());

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
    assertThat(((ServerErrorFailure) result.cause()).getReason(), is(expectedError));
  }

  @Test
  public void shouldForwardFailureIfAnAccountIsNotRefunded() throws Exception {
    final String expectedError = "Fee fine account failed to be refunded";

    when(accountClient.put(anyString(), any())).thenAnswer(putRespondAndFail());

    when(accountActionsClient.post(any(JsonObject.class)))
      .thenReturn(completedFuture(succeeded(emptyJsonResponse(201))));

    final Result<Void> result = feeFineService.refundAndCloseAccounts(Arrays.asList(
      refundCommand(), refundCommand())).get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
    assertThat(((ServerErrorFailure) result.cause()).getReason(), is(expectedError));
  }

  private CreateAccountCommand.Builder createCommandBuilder() {
    final Item item = Item.from(new JsonObject())
      .withLocation(Location.from(new JsonObject().put("name", "Main library")));

    return CreateAccountCommand.builder()
      .withAmount(new FeeAmount(10.0))
      .withCurrentServicePointId("cd1-id")
      .withFeeFine(new FeeFine("fee-id", "owner-id", "Lost item fee"))
      .withFeeFineOwner(new FeeFineOwner("owner-id", "Cd1 owner", singletonList("sp-id")))
      .withStaffUserId("user-id")
      .withItem(item)
      .withLoan(Loan.from(new JsonObject().put("id", UUID.randomUUID().toString())));
  }

  private RefundAccountCommand refundCommand() {
    final JsonObject account = new JsonObject()
      .put("feeFineType", "Lost item fee")
      .put("amount", 50.0)
      .put("remaining", 50.0)
      .put("id", UUID.randomUUID().toString());

    return new RefundAccountCommand(Account.from(account)
      .withFeeFineActions(emptyList()), "user-id", "sp-id");
  }

  private Response jsonResponse(int status, JsonObject json) {
    return new Response(status, json.toString(), "application/json");
  }

  private Response emptyJsonResponse(int status) {
    return jsonResponse(status, new JsonObject());
  }

  private Answer<CompletableFuture<Result<Response>>> postRespondWithRequestAndFail() {
    return new Answer<CompletableFuture<Result<Response>>>() {
      private int requestNumber = 0;

      @Override
      public CompletableFuture<Result<Response>> answer(InvocationOnMock mock) {
        requestNumber++;

        return requestNumber > 1
          ? completedFuture(succeeded(jsonResponse(201, mock.getArgument(0))))
          : completedFuture(failed(new ServerErrorFailure("Fee fine account failed to be created")));
      }
    };
  }

  private Answer<CompletableFuture<Result<Response>>> putRespondAndFail() {
    return new Answer<CompletableFuture<Result<Response>>>() {
      private int requestNumber = 0;

      @Override
      public CompletableFuture<Result<Response>> answer(InvocationOnMock mock) {
        requestNumber++;

        return requestNumber > 1
          ? completedFuture(succeeded(emptyJsonResponse(204)))
          : completedFuture(failed(new ServerErrorFailure("Fee fine account failed to be refunded")));
      }
    };
  }
}
