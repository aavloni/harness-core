package io.harness.cvng.core.services.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.core.entities.DeletedCVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.DeletedCVConfigService;
import io.harness.cvng.dashboard.services.api.AnomalyService;
import io.harness.persistence.HPersistence;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class CVConfigServiceImpl implements CVConfigService {
  @Inject private HPersistence hPersistence;
  @Inject private AnomalyService anomalyService;
  @Inject private DeletedCVConfigService deletedCVConfigService;

  @Override
  public CVConfig save(CVConfig cvConfig) {
    checkArgument(cvConfig.getUuid() == null, "UUID should be null when creating CVConfig");
    cvConfig.validate();
    hPersistence.save(cvConfig);
    return cvConfig;
  }

  @Override
  public List<CVConfig> save(List<CVConfig> cvConfigs) {
    return cvConfigs.stream().map(this ::save).collect(Collectors.toList());
  }

  @Nullable
  @Override
  public CVConfig get(@NotNull String cvConfigId) {
    return hPersistence.get(CVConfig.class, cvConfigId);
  }

  @Override
  public List<CVConfig> find(String accountId, List<DataSourceType> dataSourceTypes) {
    Preconditions.checkNotNull(accountId);
    List<CVConfig> cvConfigs =
        hPersistence.createQuery(CVConfig.class).filter(CVConfigKeys.accountId, accountId).asList();
    return cvConfigs.stream()
        .filter(cvConfig -> dataSourceTypes.contains(cvConfig.getType()))
        .collect(Collectors.toList());
  }

  @Override
  public void update(CVConfig cvConfig) {
    checkNotNull(cvConfig.getUuid(), "Trying to update a CVConfig with empty UUID.");
    cvConfig.validate();
    hPersistence.save(cvConfig);
  }

  @Override
  public void update(List<CVConfig> cvConfigs) {
    cvConfigs.forEach(cvConfig -> cvConfig.validate());
    cvConfigs.forEach(this ::update); // TODO: implement batch update
  }

  @Override
  public void delete(@NotNull String cvConfigId) {
    CVConfig cvConfig = get(cvConfigId);
    if (cvConfig == null) {
      return;
    }
    deletedCVConfigService.save(DeletedCVConfig.builder()
                                    .cvConfig(cvConfig)
                                    .accountId(cvConfig.getAccountId())
                                    .dataCollectionTaskId(cvConfig.getDataCollectionTaskId())
                                    .build());
    anomalyService.closeAnomaly(cvConfig.getAccountId(), cvConfigId, Instant.now());
    hPersistence.delete(CVConfig.class, cvConfigId);
  }

  @Override
  public void deleteByGroupId(String accountId, String connectorId, String productName, String groupId) {
    hPersistence.delete(hPersistence.createQuery(CVConfig.class)
                            .filter(CVConfigKeys.accountId, accountId)
                            .filter(CVConfigKeys.connectorId, connectorId)
                            .filter(CVConfigKeys.productName, productName)
                            .filter(CVConfigKeys.groupId, groupId));
  }

  @Override
  public List<CVConfig> list(@NotNull String accountId, String connectorId) {
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class)
                                .filter(CVConfigKeys.accountId, accountId)
                                .filter(CVConfigKeys.connectorId, connectorId);
    return query.asList();
  }
  @Override
  public List<CVConfig> list(String accountId, String connectorId, String productName) {
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class)
                                .filter(CVConfigKeys.accountId, accountId)
                                .filter(CVConfigKeys.connectorId, connectorId)
                                .filter(CVConfigKeys.productName, productName);
    return query.asList();
  }
  @Override
  public List<CVConfig> list(String accountId, String connectorId, String productName, String groupId) {
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class)
                                .filter(CVConfigKeys.accountId, accountId)
                                .filter(CVConfigKeys.connectorId, connectorId)
                                .filter(CVConfigKeys.productName, productName)
                                .filter(CVConfigKeys.groupId, groupId);
    return query.asList();
  }

  @Override
  public List<String> getProductNames(String accountId, String connectorId) {
    checkNotNull(accountId, "accountId can not be null");
    checkNotNull(connectorId, "ConnectorId can not be null");
    return hPersistence.createQuery(CVConfig.class)
        .filter(CVConfigKeys.connectorId, connectorId)
        .filter(CVConfigKeys.accountId, accountId)
        .project(CVConfigKeys.productName, true)
        .asList()
        .stream()
        .map(cvConfig -> cvConfig.getProductName())
        .distinct()
        .sorted()
        .collect(toList());
  }

  @Override
  public void setCollectionTaskId(String uuid, String dataCollectionTaskId) {
    UpdateOperations<CVConfig> updateOperations = hPersistence.createUpdateOperations(CVConfig.class)
                                                      .set(CVConfigKeys.dataCollectionTaskId, dataCollectionTaskId);
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class).filter(CVConfigKeys.uuid, uuid);
    hPersistence.update(query, updateOperations);
  }
}
