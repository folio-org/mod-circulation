package org.folio.circulation.support.request;

import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.HoldingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;

import lombok.Getter;

@Getter
public class RequestRelatedRepositories {
  private UserRepository userRepository;
  private ItemRepository itemRepository;
  private InstanceRepository instanceRepository;
  private HoldingsRepository holdingsRepository;
  private LoanRepository loanRepository;
  private LoanPolicyRepository loanPolicyRepository;
  private RequestRepository requestRepository;
  private RequestQueueRepository requestQueueRepository;
  private RequestPolicyRepository requestPolicyRepository;
  private ConfigurationRepository configurationRepository;
  private SettingsRepository settingsRepository;
  private ServicePointRepository servicePointRepository;
  private LocationRepository locationRepository;

  public RequestRelatedRepositories(Clients clients) {
    userRepository = new UserRepository(clients);
    itemRepository = new ItemRepository(clients);
    instanceRepository = new InstanceRepository(clients);
    holdingsRepository = new HoldingsRepository(clients.holdingsStorage());
    loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    loanPolicyRepository = new LoanPolicyRepository(clients);
    requestRepository = RequestRepository.using(clients, itemRepository,
      userRepository, loanRepository);
    requestQueueRepository = new RequestQueueRepository(requestRepository);
    requestPolicyRepository = new RequestPolicyRepository(clients);
    configurationRepository = new ConfigurationRepository(clients);
    settingsRepository = new SettingsRepository(clients);
    servicePointRepository = new ServicePointRepository(clients);
    locationRepository = LocationRepository.using(clients);
  }
}
