package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.USAGE_LIMITS_EXCEEDED;
import static io.harness.exception.WingsException.USER;
import static io.harness.persistence.HQuery.excludeValidate;
import static io.harness.validation.PersistenceValidator.duplicateCheck;
import static io.harness.validation.Validator.equalCheck;
import static io.harness.validation.Validator.notNullCheck;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.atteo.evo.inflector.English.plural;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;
import static software.wings.beans.Application.GLOBAL_APP_ID;
import static software.wings.beans.Environment.GLOBAL_ENV_ID;
import static software.wings.beans.GitConfig.GIT_USER;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.SettingAttribute.ENV_ID_KEY;
import static software.wings.beans.SettingAttribute.NAME_KEY;
import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;
import static software.wings.beans.SettingAttribute.VALUE_TYPE_KEY;
import static software.wings.beans.StringValue.Builder.aStringValue;
import static software.wings.common.PathConstants.BACKUP_PATH;
import static software.wings.common.PathConstants.DEFAULT_BACKUP_PATH;
import static software.wings.common.PathConstants.DEFAULT_RUNTIME_PATH;
import static software.wings.common.PathConstants.DEFAULT_STAGING_PATH;
import static software.wings.common.PathConstants.DEFAULT_WINDOWS_RUNTIME_PATH;
import static software.wings.common.PathConstants.RUNTIME_PATH;
import static software.wings.common.PathConstants.STAGING_PATH;
import static software.wings.common.PathConstants.WINDOWS_RUNTIME_PATH;
import static software.wings.service.impl.ArtifactStreamServiceImpl.addFilterToArtifactStreamQuery;
import static software.wings.service.intfc.security.SecretManager.ENCRYPTED_FIELD_MASK;
import static software.wings.settings.SettingValue.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingValue.SettingVariableTypes.GCS_HELM_REPO;
import static software.wings.utils.UsageRestrictionsUtils.getAllAppAllEnvUsageRestrictions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.ccm.CCMSettingService;
import io.harness.ccm.CloudCostAware;
import io.harness.data.parser.CsvParser;
import io.harness.data.structure.EmptyPredicate;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedUsageRestrictionsException;
import io.harness.exception.WingsException;
import io.harness.observer.Rejection;
import io.harness.observer.Subject;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.HIterator;
import io.harness.queue.QueuePublisher;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.validation.Create;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AccountEvent;
import software.wings.beans.AccountEventType;
import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.Event.Type;
import software.wings.beans.FeatureName;
import software.wings.beans.GitConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.ServiceVariable;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.StringValue;
import software.wings.beans.ValidationResult;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.features.GitOpsFeature;
import software.wings.features.api.UsageLimitedFeature;
import software.wings.prune.PruneEvent;
import software.wings.security.PermissionAttribute.Action;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.BuildService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.FeatureFlagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.manipulation.SettingsServiceManipulationObserver;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.RestrictionsAndAppEnvMap;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingValue.SettingVariableTypes;
import software.wings.settings.UsageRestrictions;
import software.wings.utils.ArtifactType;
import software.wings.utils.CacheManager;
import software.wings.utils.CryptoUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by anubhaw on 5/17/16.
 */
@Slf4j
@ValidateOnExecution
@Singleton
public class SettingsServiceImpl implements SettingsService {
  @Inject private Map<Class<? extends SettingValue>, Class<? extends BuildService>> buildServiceMap;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private ServiceClassLocator serviceLocator;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private SettingValidationService settingValidationService;
  @Inject private AppService appService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Transient @Inject private SecretManager secretManager;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Inject private UserService userService;
  @Inject private YamlPushService yamlPushService;
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject private AccountService accountService;
  @Inject private ArtifactService artifactService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private CCMSettingService ccmSettingService;

  @Getter private Subject<SettingsServiceManipulationObserver> manipulationSubject = new Subject<>();
  @Inject private CacheManager cacheManager;
  @Inject private EnvironmentService envService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject @Named(GitOpsFeature.FEATURE_NAME) private UsageLimitedFeature gitOpsFeature;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private WorkflowService workflowService;
  @Inject private QueuePublisher<PruneEvent> pruneQueue;

  @Inject @Getter private Subject<SettingAttributeObserver> subject = new Subject<>();

  @Override
  public PageResponse<SettingAttribute> list(
      PageRequest<SettingAttribute> req, String appIdFromRequest, String envIdFromRequest) {
    try {
      PageResponse<SettingAttribute> pageResponse = wingsPersistence.query(SettingAttribute.class, req);

      List<SettingAttribute> filteredSettingAttributes =
          getFilteredSettingAttributes(pageResponse.getResponse(), appIdFromRequest, envIdFromRequest);

      return aPageResponse()
          .withResponse(filteredSettingAttributes)
          .withTotal(filteredSettingAttributes.size())
          .build();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public PageResponse<SettingAttribute> list(PageRequest<SettingAttribute> req, String appIdFromRequest,
      String envIdFromRequest, String accountId, boolean gitSshConfigOnly, boolean withArtifactStreamCount,
      String artifactStreamSearchString, int maxArtifactStreams, ArtifactType artifactType) {
    try {
      PageRequest<SettingAttribute> pageRequest = req.copy();
      int offset = pageRequest.getStart();
      int limit = pageRequest.getPageSize();

      pageRequest.setOffset("0");
      pageRequest.setLimit(String.valueOf(Integer.MAX_VALUE));
      PageResponse<SettingAttribute> pageResponse = wingsPersistence.query(SettingAttribute.class, pageRequest);

      List<SettingAttribute> filteredSettingAttributes =
          getFilteredSettingAttributes(pageResponse.getResponse(), appIdFromRequest, envIdFromRequest);

      if (gitSshConfigOnly) {
        filteredSettingAttributes =
            filteredSettingAttributes.stream()
                .filter(settingAttribute
                    -> GIT_USER.equals(((HostConnectionAttributes) settingAttribute.getValue()).getUserName()))
                .collect(Collectors.toList());
      }

      if (withArtifactStreamCount && isNotEmpty(filteredSettingAttributes)) {
        filteredSettingAttributes = filterSettingsAndArtifactStreamsWithCount(
            accountId, artifactStreamSearchString, maxArtifactStreams, artifactType, filteredSettingAttributes);
      }

      List<SettingAttribute> resp;
      if (isEmpty(filteredSettingAttributes)) {
        resp = Collections.emptyList();
      } else {
        int total = filteredSettingAttributes.size();
        if (total <= offset) {
          resp = Collections.emptyList();
        } else {
          int endIdx = Math.min(offset + limit, total);
          resp = filteredSettingAttributes.subList(offset, endIdx);
        }
      }

      return aPageResponse()
          .withResponse(resp)
          .withTotal(filteredSettingAttributes.size())
          .withOffset(req.getOffset())
          .withLimit(req.getLimit())
          .build();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private List<SettingAttribute> filterSettingsAndArtifactStreamsWithCount(String accountId,
      String artifactStreamSearchString, int maxArtifactStreams, ArtifactType artifactType,
      List<SettingAttribute> filteredSettingAttributes) {
    List<SettingAttribute> newSettingAttributes = new ArrayList<>();
    for (SettingAttribute settingAttribute : filteredSettingAttributes) {
      Query<ArtifactStream> artifactStreamQuery = wingsPersistence.createQuery(ArtifactStream.class)
                                                      .disableValidation()
                                                      .filter(ArtifactStreamKeys.settingId, settingAttribute.getUuid())
                                                      .filter(ArtifactStreamKeys.accountId, accountId)
                                                      .project(ArtifactStreamKeys.settingId, true)
                                                      .project(ArtifactStreamKeys.name, true)
                                                      .project(ArtifactStreamKeys.sourceName, true)
                                                      .order(Sort.descending(CreatedAtAware.CREATED_AT_KEY));
      if (isNotEmpty(artifactStreamSearchString)) {
        artifactStreamQuery.field(ArtifactStreamKeys.name).containsIgnoreCase(artifactStreamSearchString);
      }

      addFilterToArtifactStreamQuery(artifactType, artifactStreamQuery);
      long totalArtifactStreamCount = artifactStreamQuery.count();
      if (totalArtifactStreamCount == 0L) {
        continue;
      }
      List<ArtifactStream> artifactStreams = artifactStreamQuery.asList(new FindOptions().limit(maxArtifactStreams));
      if (isEmpty(artifactStreams)) {
        continue;
      }

      List<ArtifactStreamSummary> artifactStreamSummaries = new ArrayList<>();
      artifactStreams.forEach(artifactStream -> {
        Artifact lastCollectedArtifact = artifactService.fetchLastCollectedArtifact(artifactStream);
        ArtifactStreamSummary artifactStreamSummary =
            ArtifactStreamSummary.prepareSummaryFromArtifactStream(artifactStream, lastCollectedArtifact);
        artifactStreamSummaries.add(artifactStreamSummary);
      });
      settingAttribute.setArtifactStreamCount(totalArtifactStreamCount);
      settingAttribute.setArtifactStreams(artifactStreamSummaries);
      newSettingAttributes.add(settingAttribute);
    }
    return newSettingAttributes;
  }

  @Override
  public List<SettingAttribute> getFilteredSettingAttributes(
      List<SettingAttribute> inputSettingAttributes, String appIdFromRequest, String envIdFromRequest) {
    if (inputSettingAttributes == null) {
      return Collections.emptyList();
    }

    if (inputSettingAttributes.size() == 0) {
      return inputSettingAttributes;
    }

    String accountId = inputSettingAttributes.get(0).getAccountId();
    List<SettingAttribute> filteredSettingAttributes = Lists.newArrayList();

    RestrictionsAndAppEnvMap restrictionsAndAppEnvMap =
        usageRestrictionsService.getRestrictionsAndAppEnvMapFromCache(accountId, Action.READ);
    Map<String, Set<String>> appEnvMapFromUserPermissions = restrictionsAndAppEnvMap.getAppEnvMap();
    UsageRestrictions restrictionsFromUserPermissions = restrictionsAndAppEnvMap.getUsageRestrictions();

    boolean isAccountAdmin = userService.isAccountAdmin(accountId);

    Set<String> appsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMap = envService.getAppIdEnvMap(appsByAccountId);

    Set<SettingAttribute> helmRepoSettingAttributes = new HashSet<>();

    inputSettingAttributes.forEach(settingAttribute -> {
      if (isSettingAttributeReferencingCloudProvider(settingAttribute)) {
        helmRepoSettingAttributes.add(settingAttribute);
      } else {
        UsageRestrictions usageRestrictionsFromEntity = settingAttribute.getUsageRestrictions();

        if (isFilteredSettingAttribute(appIdFromRequest, envIdFromRequest, accountId, appEnvMapFromUserPermissions,
                restrictionsFromUserPermissions, isAccountAdmin, appIdEnvMap, settingAttribute,
                usageRestrictionsFromEntity)) {
          filteredSettingAttributes.add(settingAttribute);
        }
      }
    });

    getFilteredHelmRepoSettingAttributes(appIdFromRequest, envIdFromRequest, accountId, filteredSettingAttributes,
        appEnvMapFromUserPermissions, restrictionsFromUserPermissions, isAccountAdmin, appIdEnvMap,
        helmRepoSettingAttributes);

    return filteredSettingAttributes;
  }

  private void checkGitConnectorsUsageWithinLimit(SettingAttribute settingAttribute) {
    int maxGitConnectorsAllowed = gitOpsFeature.getMaxUsageAllowedForAccount(settingAttribute.getAccountId());
    PageRequest<SettingAttribute> request =
        aPageRequest()
            .addFilter(SettingAttributeKeys.accountId, Operator.EQ, settingAttribute.getAccountId())
            .addFilter(SettingAttribute.VALUE_TYPE_KEY, Operator.EQ, SettingVariableTypes.GIT)
            .build();
    int currentGitConnectorCount = list(request, null, null).getResponse().size();
    if (currentGitConnectorCount >= maxGitConnectorsAllowed) {
      logger.info("Did not save Setting Attribute of type {} for account ID {} because usage limit exceeded",
          settingAttribute.getValue().getType(), settingAttribute.getAccountId());
      throw new WingsException(USAGE_LIMITS_EXCEEDED,
          String.format("Cannot create more than %d Git Connector", maxGitConnectorsAllowed), WingsException.USER);
    }
  }

  private void getFilteredHelmRepoSettingAttributes(String appIdFromRequest, String envIdFromRequest, String accountId,
      List<SettingAttribute> filteredSettingAttributes, Map<String, Set<String>> appEnvMapFromUserPermissions,
      UsageRestrictions restrictionsFromUserPermissions, boolean isAccountAdmin, Map<String, List<Base>> appIdEnvMap,
      Set<SettingAttribute> helmRepoSettingAttributes) {
    if (isNotEmpty(helmRepoSettingAttributes)) {
      Set<String> cloudProviderIds = new HashSet<>();

      helmRepoSettingAttributes.forEach(settingAttribute -> {
        HelmRepoConfig helmRepoConfig = (HelmRepoConfig) settingAttribute.getValue();
        if (isNotBlank(helmRepoConfig.getConnectorId())) {
          cloudProviderIds.add(helmRepoConfig.getConnectorId());
        }
      });

      Map<String, SettingAttribute> cloudProvidersMap = new HashMap<>();

      wingsPersistence.createQuery(SettingAttribute.class)
          .filter(SettingAttributeKeys.accountId, accountId)
          .filter(SettingAttributeKeys.appId, GLOBAL_APP_ID)
          .field(ID_KEY)
          .in(cloudProviderIds)
          .forEach(settingAttribute -> { cloudProvidersMap.put(settingAttribute.getUuid(), settingAttribute); });

      helmRepoSettingAttributes.forEach(settingAttribute -> {
        String cloudProviderId = ((HelmRepoConfig) settingAttribute.getValue()).getConnectorId();
        if (isNotBlank(cloudProviderId) && cloudProvidersMap.containsKey(cloudProviderId)) {
          UsageRestrictions usageRestrictionsFromEntity = cloudProvidersMap.get(cloudProviderId).getUsageRestrictions();

          if (isFilteredSettingAttribute(appIdFromRequest, envIdFromRequest, accountId, appEnvMapFromUserPermissions,
                  restrictionsFromUserPermissions, isAccountAdmin, appIdEnvMap, settingAttribute,
                  usageRestrictionsFromEntity)) {
            filteredSettingAttributes.add(settingAttribute);
          }
        }
      });
    }
  }

  private boolean isFilteredSettingAttribute(String appIdFromRequest, String envIdFromRequest, String accountId,
      Map<String, Set<String>> appEnvMapFromUserPermissions, UsageRestrictions restrictionsFromUserPermissions,
      boolean isAccountAdmin, Map<String, List<Base>> appIdEnvMap, SettingAttribute settingAttribute,
      UsageRestrictions usageRestrictionsFromEntity) {
    if (usageRestrictionsService.hasAccess(accountId, isAccountAdmin, appIdFromRequest, envIdFromRequest,
            usageRestrictionsFromEntity, restrictionsFromUserPermissions, appEnvMapFromUserPermissions, appIdEnvMap)) {
      // HAR-7726: Mask the encrypted field values when listing all settings.
      SettingValue settingValue = settingAttribute.getValue();
      if (settingValue instanceof EncryptableSetting) {
        secretManager.maskEncryptedFields((EncryptableSetting) settingValue);
      }
      return true;
    }

    return false;
  }

  private boolean isSettingAttributeReferencingCloudProvider(SettingAttribute settingAttribute) {
    return SettingCategory.HELM_REPO.equals(settingAttribute.getCategory())
        && (AMAZON_S3_HELM_REPO.name().equals(settingAttribute.getValue().getType())
               || GCS_HELM_REPO.name().equals(settingAttribute.getValue().getType()));
  }

  private UsageRestrictions getUsageRestriction(SettingAttribute settingAttribute) {
    SettingValue settingValue = settingAttribute.getValue();
    if (isSettingAttributeReferencingCloudProvider(settingAttribute)) {
      String cloudProviderId = ((HelmRepoConfig) settingValue).getConnectorId();
      SettingAttribute cloudProvider = get(settingAttribute.getAppId(), cloudProviderId);
      if (cloudProvider == null) {
        throw new InvalidRequestException("Cloud provider doesn't exist", USER);
      }
      return cloudProvider.getUsageRestrictions();
    } else {
      return settingAttribute.getUsageRestrictions();
    }
  }

  @Override
  @ValidationGroups(Create.class)
  public SettingAttribute save(SettingAttribute settingAttribute) {
    if (settingAttribute.getValue().getType().equals(SettingVariableTypes.GIT.name())) {
      checkGitConnectorsUsageWithinLimit(settingAttribute);
    }
    return save(settingAttribute, true);
  }

  @Override
  @ValidationGroups(Create.class)
  public SettingAttribute forceSave(SettingAttribute settingAttribute) {
    usageRestrictionsService.validateUsageRestrictionsOnEntitySave(
        settingAttribute.getAccountId(), getUsageRestriction(settingAttribute));

    if (settingAttribute.getValue() != null) {
      if (settingAttribute.getValue() instanceof EncryptableSetting) {
        ((EncryptableSetting) settingAttribute.getValue()).setAccountId(settingAttribute.getAccountId());
      }
    }

    SettingAttribute createdSettingAttribute =
        duplicateCheck(()
                           -> wingsPersistence.saveAndGet(SettingAttribute.class, settingAttribute),
            "name", settingAttribute.getName());
    if (createdSettingAttribute != null && !createdSettingAttribute.isSample()) {
      if (SettingCategory.CLOUD_PROVIDER.equals(createdSettingAttribute.getCategory())) {
        eventPublishHelper.publishAccountEvent(settingAttribute.getAccountId(),
            AccountEvent.builder().accountEventType(AccountEventType.CLOUD_PROVIDER_CREATED).build(), true, true);
      } else if (SettingCategory.CONNECTOR.equals(createdSettingAttribute.getCategory())
          && isArtifactServer(createdSettingAttribute.getValue().getSettingType())) {
        eventPublishHelper.publishAccountEvent(settingAttribute.getAccountId(),
            AccountEvent.builder().accountEventType(AccountEventType.ARTIFACT_REPO_CREATED).build(), true, true);
      }
    }
    return createdSettingAttribute;
  }

  private boolean isArtifactServer(SettingVariableTypes settingVariableTypes) {
    switch (settingVariableTypes) {
      case JENKINS:
      case BAMBOO:
      case DOCKER:
      case NEXUS:
      case ARTIFACTORY:
      case SMB:
      case SMTP:
      case AMAZON_S3_HELM_REPO:
      case GCS_HELM_REPO:
      case HTTP_HELM_REPO:
      case AZURE_ARTIFACTS_PAT:
        return true;
      default:
        return false;
    }
  }

  @Override
  public ValidationResult validateConnectivity(SettingAttribute settingAttribute) {
    try {
      SettingAttribute existingSetting = get(settingAttribute.getAppId(), settingAttribute.getUuid());
      if (existingSetting != null) {
        resetUnchangedEncryptedFields(existingSetting, settingAttribute);
      }

      return settingValidationService.validateConnectivity(settingAttribute);
    } catch (Exception ex) {
      return new ValidationResult(false, ExceptionUtils.getMessage(ex));
    }
  }

  private ValidationResult validateInternal(final SettingAttribute settingAttribute) {
    try {
      return new ValidationResult(settingValidationService.validate(settingAttribute), "");
    } catch (Exception ex) {
      return new ValidationResult(false, ExceptionUtils.getMessage(ex));
    }
  }

  @Override
  public ValidationResult validate(final SettingAttribute settingAttribute) {
    return validateInternal(settingAttribute);
  }

  @Override
  public ValidationResult validate(final String varId) {
    final SettingAttribute settingAttribute = get(varId);
    if (settingAttribute != null) {
      return validateInternal(settingAttribute);
    } else {
      return new ValidationResult(false, format("Setting Attribute with id: %s does not exist.", varId));
    }
  }

  @Override
  public Map<String, String> listAccountDefaults(String accountId) {
    return listAccountOrAppDefaults(accountId, GLOBAL_APP_ID);
  }

  @Override
  public Map<String, String> listAppDefaults(String accountId, String appId) {
    return listAccountOrAppDefaults(accountId, appId);
  }

  private Map<String, String> listAccountOrAppDefaults(String accountId, String appId) {
    List<SettingAttribute> settingAttributes = wingsPersistence.createQuery(SettingAttribute.class)
                                                   .filter(SettingAttributeKeys.accountId, accountId)
                                                   .filter(SettingAttributeKeys.appId, appId)
                                                   .filter(VALUE_TYPE_KEY, SettingVariableTypes.STRING.name())
                                                   .asList();

    return settingAttributes.stream().collect(Collectors.toMap(SettingAttribute::getName,
        settingAttribute
        -> Optional.ofNullable(((StringValue) settingAttribute.getValue()).getValue()).orElse(""),
        (a, b) -> b));
  }

  @Override
  @ValidationGroups(Create.class)
  public SettingAttribute save(SettingAttribute settingAttribute, boolean pushToGit) {
    settingValidationService.validate(settingAttribute);
    // e.g. User is saving GitConnector and setWebhookToken is needed.
    // This fields is populated by us and not by user
    autoGenerateFieldsIfRequired(settingAttribute);
    SettingAttribute newSettingAttribute = forceSave(settingAttribute);

    if (shouldBeSynced(newSettingAttribute, pushToGit)) {
      yamlPushService.pushYamlChangeSet(settingAttribute.getAccountId(), null, newSettingAttribute, Type.CREATE,
          settingAttribute.isSyncFromGit(), false);
    } else {
      auditServiceHelper.reportForAuditingUsingAccountId(
          settingAttribute.getAccountId(), null, newSettingAttribute, Type.CREATE);
    }

    return newSettingAttribute;
  }

  private void autoGenerateFieldsIfRequired(SettingAttribute settingAttribute) {
    if (settingAttribute.getValue() instanceof GitConfig) {
      GitConfig gitConfig = (GitConfig) settingAttribute.getValue();

      if (gitConfig.isGenerateWebhookUrl() && isEmpty(gitConfig.getWebhookToken())) {
        gitConfig.setWebhookToken(CryptoUtils.secureRandAlphaNumString(40));
      }
    }
  }

  private boolean shouldBeSynced(SettingAttribute settingAttribute, boolean pushToGit) {
    String type = settingAttribute.getValue().getType();

    boolean skip = SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES.name().equals(type)
        || SettingVariableTypes.BASTION_HOST_CONNECTION_ATTRIBUTES.name().equals(type)
        || SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES.name().equals(type);

    return pushToGit && !skip;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#get(java.lang.String, java.lang.String)
   */
  @Override
  public SettingAttribute get(String appId, String varId) {
    return get(appId, GLOBAL_ENV_ID, varId);
  }

  @Override
  public SettingAttribute get(String appId, String envId, String varId) {
    return wingsPersistence.createQuery(SettingAttribute.class)
        .filter("appId", appId)
        .filter(SettingAttributeKeys.envId, envId)
        .filter(ID_KEY, varId)
        .get();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#get(java.lang.String)
   */
  @Override
  public SettingAttribute get(String varId) {
    SettingAttribute settingAttribute = wingsPersistence.get(SettingAttribute.class, varId);
    setInternal(settingAttribute);
    return settingAttribute;
  }

  private void setInternal(SettingAttribute settingAttribute) {
    if (settingAttribute != null && settingAttribute.getValue() instanceof GitConfig) {
      GitConfig gitConfig = (GitConfig) settingAttribute.getValue();
      gitConfigHelperService.setSshKeySettingAttributeIfNeeded(gitConfig);
    }
  }

  @Override
  public SettingAttribute getSettingAttributeByName(String accountId, String settingAttributeName) {
    return wingsPersistence.createQuery(SettingAttribute.class)
        .filter(SettingAttributeKeys.name, settingAttributeName)
        .filter(SettingAttributeKeys.accountId, accountId)
        .get();
  }

  private void resetUnchangedEncryptedFields(
      SettingAttribute existingSettingAttribute, SettingAttribute newSettingAttribute) {
    if (EncryptableSetting.class.isInstance(existingSettingAttribute.getValue())) {
      EncryptableSetting object = (EncryptableSetting) existingSettingAttribute.getValue();
      object.setDecrypted(false);

      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails(object, newSettingAttribute.getAppId(), null);
      managerDecryptionService.decrypt(object, encryptionDetails);

      secretManager.resetUnchangedEncryptedFields((EncryptableSetting) existingSettingAttribute.getValue(),
          (EncryptableSetting) newSettingAttribute.getValue());
    }
  }

  @Override
  public SettingAttribute update(SettingAttribute settingAttribute, boolean pushToGit) {
    SettingAttribute existingSetting = get(settingAttribute.getAppId(), settingAttribute.getUuid());
    SettingAttribute prevSettingAttribute = existingSetting;

    notNullCheck("Setting Attribute was deleted", existingSetting, USER);
    notNullCheck("SettingValue not associated", settingAttribute.getValue(), USER);
    equalCheck(existingSetting.getValue().getType(), settingAttribute.getValue().getType());
    validateSettingAttribute(settingAttribute, existingSetting);
    usageRestrictionsService.validateUsageRestrictionsOnEntityUpdate(
        settingAttribute.getAccountId(), existingSetting.getUsageRestrictions(), getUsageRestriction(settingAttribute));

    settingAttribute.setAccountId(existingSetting.getAccountId());
    settingAttribute.setAppId(existingSetting.getAppId());
    // e.g. User is saving GitConnector and setWebhookToken is needed.
    // This fields is populated by us and not by user
    autoGenerateFieldsIfRequired(settingAttribute);

    resetUnchangedEncryptedFields(existingSetting, settingAttribute);
    settingValidationService.validate(settingAttribute);

    SettingAttribute savedSettingAttributes = get(settingAttribute.getUuid());

    ImmutableMap.Builder<String, Object> fields =
        ImmutableMap.<String, Object>builder().put("name", settingAttribute.getName());

    // To revisit
    if (settingAttribute.getUsageRestrictions() != null) {
      fields.put("usageRestrictions", settingAttribute.getUsageRestrictions());
    }

    if (settingAttribute.getValue() != null) {
      if (settingAttribute.getValue() instanceof EncryptableSetting) {
        ((EncryptableSetting) settingAttribute.getValue()).setAccountId(settingAttribute.getAccountId());
      }
      fields.put("value", settingAttribute.getValue());
    }
    wingsPersistence.updateFields(SettingAttribute.class, settingAttribute.getUuid(), fields.build());

    SettingAttribute updatedSettingAttribute = wingsPersistence.get(SettingAttribute.class, settingAttribute.getUuid());
    if (!shouldBeSynced(settingAttribute, true)) {
      auditServiceHelper.reportForAuditingUsingAccountId(
          settingAttribute.getAccountId(), existingSetting, updatedSettingAttribute, Type.UPDATE);
    }

    // Need to mask the private key field value before the value is returned.
    // This will avoid confusing the user that the key field is empty when it's not.
    SettingValue updatedSettingValue = updatedSettingAttribute.getValue();
    if (updatedSettingValue instanceof HostConnectionAttributes) {
      HostConnectionAttributes hostConnectionAttributes = (HostConnectionAttributes) updatedSettingValue;
      if (!hostConnectionAttributes.isKeyless()) {
        hostConnectionAttributes.setKey(ENCRYPTED_FIELD_MASK.toCharArray());
      }
    }

    if (shouldBeSynced(updatedSettingAttribute, pushToGit)) {
      boolean isRename = !savedSettingAttributes.getName().equals(updatedSettingAttribute.getName());
      yamlPushService.pushYamlChangeSet(settingAttribute.getAccountId(), savedSettingAttributes,
          updatedSettingAttribute, Type.UPDATE, settingAttribute.isSyncFromGit(), isRename);
    }
    cacheManager.getNewRelicApplicationCache().remove(updatedSettingAttribute.getUuid());

    if (updatedSettingAttribute.getValue() instanceof CloudCostAware) {
      ccmSettingService.maskCCMConfig(updatedSettingAttribute);
    }

    try {
      if (CLOUD_PROVIDER == settingAttribute.getCategory()) {
        subject.fireInform(SettingAttributeObserver::onUpdated, prevSettingAttribute, settingAttribute);
      }
    } catch (Exception e) {
      logger.error("Encountered exception while informing the observers of Cloud Providers.", e);
    }

    return updatedSettingAttribute;
  }

  private void validateSettingAttribute(SettingAttribute settingAttribute, SettingAttribute existingSettingAttribute) {
    if (settingAttribute != null && existingSettingAttribute != null) {
      if (settingAttribute.getValue() != null && existingSettingAttribute.getValue() != null) {
        if (existingSettingAttribute.getValue() instanceof NexusConfig) {
          if (!((NexusConfig) settingAttribute.getValue())
                   .getVersion()
                   .equals(((NexusConfig) existingSettingAttribute.getValue()).getVersion())) {
            throw new InvalidRequestException("Version cannot be updated", USER);
          }
        }
      }
    }
  }

  @Override
  public void updateUsageRestrictionsInternal(String uuid, UsageRestrictions usageRestrictions) {
    ImmutableMap.Builder<String, Object> fields =
        ImmutableMap.<String, Object>builder().put("usageRestrictions", usageRestrictions);
    wingsPersistence.updateFields(SettingAttribute.class, uuid, fields.build());
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#update(software.wings.beans.SettingAttribute)
   */

  @Override
  public SettingAttribute update(SettingAttribute settingAttribute) {
    return update(settingAttribute, true);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#delete(java.lang.String, java.lang.String)
   */
  @Override
  public void delete(String appId, String varId) {
    delete(appId, varId, true, false);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#delete(java.lang.String, java.lang.String)
   */
  @Override
  public void delete(String appId, String varId, boolean pushToGit, boolean syncFromGit) {
    SettingAttribute settingAttribute = get(varId);
    notNullCheck("Setting Value", settingAttribute, USER);
    String accountId = settingAttribute.getAccountId();
    if (!usageRestrictionsService.userHasPermissionsToChangeEntity(
            accountId, settingAttribute.getUsageRestrictions())) {
      throw new UnauthorizedUsageRestrictionsException(USER);
    }

    ensureSettingAttributeSafeToDelete(settingAttribute);

    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
      pruneQueue.send(new PruneEvent(SettingAttribute.class, appId, settingAttribute.getUuid()));
    }
    boolean deleted = wingsPersistence.delete(settingAttribute);

    try {
      if (CLOUD_PROVIDER == settingAttribute.getCategory()) {
        subject.fireInform(SettingAttributeObserver::onDeleted, settingAttribute);
      }
    } catch (Exception e) {
      logger.error("Encountered exception while informing the observers of Cloud Providers.", e);
    }

    if (deleted && shouldBeSynced(settingAttribute, pushToGit)) {
      yamlPushService.pushYamlChangeSet(accountId, settingAttribute, null, Type.DELETE, syncFromGit, false);
      cacheManager.getNewRelicApplicationCache().remove(settingAttribute.getUuid());
    } else {
      auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, settingAttribute);
    }
  }

  /**
   * Retain only the selected
   * @param selectedGitConnectors List of setting attribute Names of Git connectors to be retained
   */
  public boolean retainSelectedGitConnectorsAndDeleteRest(String accountId, List<String> selectedGitConnectors) {
    if (EmptyPredicate.isNotEmpty(selectedGitConnectors)) {
      // Delete git connectors
      wingsPersistence.delete(wingsPersistence.createQuery(SettingAttribute.class)
                                  .filter(SettingAttributeKeys.accountId, accountId)
                                  .filter(SettingAttribute.VALUE_TYPE_KEY, SettingVariableTypes.GIT)
                                  .field(NAME_KEY)
                                  .notIn(selectedGitConnectors));
      return true;
    }
    return false;
  }

  @Override
  public void deleteByYamlGit(String appId, String settingAttributeId, boolean syncFromGit) {
    delete(appId, settingAttributeId, true, syncFromGit);
  }

  private void ensureSettingAttributeSafeToDelete(SettingAttribute settingAttribute) {
    if (settingAttribute.getCategory().equals(SettingCategory.CLOUD_PROVIDER)) {
      ensureCloudProviderSafeToDelete(settingAttribute);
    } else if (settingAttribute.getCategory().equals(SettingCategory.CONNECTOR)) {
      ensureConnectorSafeToDelete(settingAttribute);
    } else if (settingAttribute.getCategory().equals(SettingCategory.SETTING)) {
      ensureSettingSafeToDelete(settingAttribute);
    } else if (settingAttribute.getCategory().equals(SettingCategory.AZURE_ARTIFACTS)) {
      ensureAzureArtifactsConnectorSafeToDelete(settingAttribute);
    }
  }

  private void ensureSettingSafeToDelete(SettingAttribute settingAttribute) {
    String accountId = settingAttribute.getAccountId();
    if (featureFlagService.isEnabled(FeatureName.INFRA_MAPPING_REFACTOR, accountId)) {
      List<String> infraDefinitionNames = infrastructureDefinitionService.listNamesByConnectionAttr(
          settingAttribute.getAccountId(), settingAttribute.getUuid());
      if (isNotEmpty(infraDefinitionNames)) {
        throw new InvalidRequestException(format("Attribute [%s] is referenced by %d "
                + " %s "
                + "[%s].",
            settingAttribute.getName(), infraDefinitionNames.size(),
            plural("Infrastructure "
                    + "Definition",
                infraDefinitionNames.size()),
            join(", ", infraDefinitionNames)));
      }
    }
    // TODO:: workflow scan for finding out usage in Steps/expression ???
  }

  private void ensureConnectorSafeToDelete(SettingAttribute connectorSetting) {
    if (SettingVariableTypes.ELB.name().equals(connectorSetting.getValue().getType())) {
      List<InfrastructureMapping> infrastructureMappings =
          infrastructureMappingService
              .list(aPageRequest()
                        .addFilter("loadBalancerId", EQ, connectorSetting.getUuid())
                        .withLimit(PageRequest.UNLIMITED)
                        .build(),
                  excludeValidate)
              .getResponse();

      List<String> infraMappingNames =
          infrastructureMappings.stream().map(InfrastructureMapping::getName).collect(toList());
      if (!infraMappingNames.isEmpty()) {
        throw new InvalidRequestException(format("Connector [%s] is referenced by %d Service %s [%s].",
            connectorSetting.getName(), infraMappingNames.size(), plural("Infrastructure", infraMappingNames.size()),
            join(", ", infraMappingNames)));
      }
    } else {
      if (!featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, connectorSetting.getAccountId())) {
        List<ArtifactStream> artifactStreams = artifactStreamService.listBySettingId(connectorSetting.getUuid());
        if (!artifactStreams.isEmpty()) {
          List<String> artifactStreamNames = artifactStreams.stream()
                                                 .map(ArtifactStream::getSourceName)
                                                 .filter(java.util.Objects::nonNull)
                                                 .collect(toList());
          throw new InvalidRequestException(
              format("Connector [%s] is referenced by %d Artifact %s [%s].", connectorSetting.getName(),
                  artifactStreamNames.size(), plural("Source", artifactStreamNames.size()),
                  join(", ", artifactStreamNames)),
              USER);
        }

        List<Rejection> rejections = manipulationSubject.fireApproveFromAll(
            SettingsServiceManipulationObserver::settingsServiceDeleting, connectorSetting);
        if (isNotEmpty(rejections)) {
          throw new InvalidRequestException(
              format("[%s]", join("\n", rejections.stream().map(Rejection::message).collect(toList()))), USER);
        }
      }
    }

    // TODO:: workflow scan for finding out usage in Steps ???
  }

  private void ensureCloudProviderSafeToDelete(SettingAttribute cloudProviderSetting) {
    String accountId = cloudProviderSetting.getAccountId();
    if (featureFlagService.isEnabled(FeatureName.INFRA_MAPPING_REFACTOR, accountId)) {
      List<String> infraDefinitionNames =
          infrastructureDefinitionService.listNamesByComputeProviderId(accountId, cloudProviderSetting.getUuid());
      if (isNotEmpty(infraDefinitionNames)) {
        throw new InvalidRequestException(
            format("Cloud provider [%s] is referenced by %d Infrastructure  %s [%s].", cloudProviderSetting.getName(),
                infraDefinitionNames.size(), plural("Definition", infraDefinitionNames.size()),
                join(", ", infraDefinitionNames)),
            USER);
      }
    } else {
      List<InfrastructureMapping> infrastructureMappings = infrastructureMappingService.listByComputeProviderId(
          cloudProviderSetting.getAccountId(), cloudProviderSetting.getUuid());

      if (!infrastructureMappings.isEmpty()) {
        List<String> infraMappingNames =
            infrastructureMappings.stream().map(InfrastructureMapping::getName).collect(toList());
        throw new InvalidRequestException(
            format("Cloud provider [%s] is referenced by %d Service %s [%s].", cloudProviderSetting.getName(),
                infraMappingNames.size(), plural("Infrastructure", infraMappingNames.size()),
                join(", ", infraMappingNames)),
            USER);
      }
    }

    if (!featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
      List<ArtifactStream> artifactStreams = artifactStreamService.listBySettingId(cloudProviderSetting.getUuid());
      if (!artifactStreams.isEmpty()) {
        List<String> artifactStreamNames = artifactStreams.stream().map(ArtifactStream::getName).collect(toList());
        throw new InvalidRequestException(
            format("Cloud provider [%s] is referenced by %d Artifact %s [%s].", cloudProviderSetting.getName(),
                artifactStreamNames.size(), plural("Source", artifactStreamNames.size()),
                join(", ", artifactStreamNames)),
            USER);
      }
    }
    // TODO:: workflow scan for finding out usage in Steps ???
  }

  private void ensureAzureArtifactsConnectorSafeToDelete(SettingAttribute connectorSetting) {
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, connectorSetting.getAccountId())) {
      return;
    }

    List<ArtifactStream> artifactStreams = artifactStreamService.listBySettingId(connectorSetting.getUuid());
    if (isEmpty(artifactStreams)) {
      return;
    }

    List<String> artifactStreamNames = artifactStreams.stream()
                                           .map(ArtifactStream::getSourceName)
                                           .filter(java.util.Objects::nonNull)
                                           .collect(toList());
    throw new InvalidRequestException(
        format("Connector [%s] is referenced by %d Artifact %s [%s].", connectorSetting.getName(),
            artifactStreamNames.size(), plural("Source", artifactStreamNames.size()), join(", ", artifactStreamNames)),
        USER);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#getByName(java.lang.String, java.lang.String)
   */
  @Override
  public SettingAttribute getByName(String accountId, String appId, String attributeName) {
    return getByName(accountId, appId, GLOBAL_ENV_ID, attributeName);
  }

  @Override
  public SettingAttribute getByName(String accountId, String appId, String envId, String attributeName) {
    return wingsPersistence.createQuery(SettingAttribute.class)
        .filter(SettingAttributeKeys.accountId, accountId)
        .field("appId")
        .in(asList(appId, GLOBAL_APP_ID))
        .field("envId")
        .in(asList(envId, GLOBAL_ENV_ID))
        .filter(SettingAttributeKeys.name, attributeName)
        .get();
  }

  @Override
  public SettingAttribute fetchSettingAttributeByName(
      String accountId, String attributeName, SettingVariableTypes settingVariableTypes) {
    return wingsPersistence.createQuery(SettingAttribute.class)
        .filter(SettingAttributeKeys.accountId, accountId)
        .filter(SettingAttributeKeys.appId, GLOBAL_APP_ID)
        .filter(ENV_ID_KEY, GLOBAL_ENV_ID)
        .filter(SettingAttribute.NAME_KEY, attributeName)
        .filter(VALUE_TYPE_KEY, settingVariableTypes.name())
        .get();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#createDefaultApplicationSettings(java.lang.String)
   */
  @Override
  public void createDefaultApplicationSettings(String appId, String accountId, boolean syncFromGit) {
    SettingAttribute settingAttribute1 = aSettingAttribute()
                                             .withAppId(appId)
                                             .withAccountId(accountId)
                                             .withEnvId(GLOBAL_ENV_ID)
                                             .withName(WINDOWS_RUNTIME_PATH)
                                             .withValue(aStringValue().withValue(DEFAULT_WINDOWS_RUNTIME_PATH).build())
                                             .withUsageRestrictions(getAllAppAllEnvUsageRestrictions())
                                             .build();
    wingsPersistence.save(settingAttribute1);

    SettingAttribute settingAttribute2 = aSettingAttribute()
                                             .withAppId(appId)
                                             .withAccountId(accountId)
                                             .withEnvId(GLOBAL_ENV_ID)
                                             .withName(RUNTIME_PATH)
                                             .withValue(aStringValue().withValue(DEFAULT_RUNTIME_PATH).build())
                                             .withUsageRestrictions(getAllAppAllEnvUsageRestrictions())
                                             .build();
    wingsPersistence.save(settingAttribute2);

    SettingAttribute settingAttribute3 = aSettingAttribute()
                                             .withAppId(appId)
                                             .withAccountId(accountId)
                                             .withEnvId(GLOBAL_ENV_ID)
                                             .withName(STAGING_PATH)
                                             .withValue(aStringValue().withValue(DEFAULT_STAGING_PATH).build())
                                             .withUsageRestrictions(getAllAppAllEnvUsageRestrictions())
                                             .build();
    wingsPersistence.save(settingAttribute3);

    SettingAttribute settingAttribute4 = aSettingAttribute()
                                             .withAppId(appId)
                                             .withAccountId(accountId)
                                             .withEnvId(GLOBAL_ENV_ID)
                                             .withName(BACKUP_PATH)
                                             .withValue(aStringValue().withValue(DEFAULT_BACKUP_PATH).build())
                                             .withUsageRestrictions(getAllAppAllEnvUsageRestrictions())
                                             .build();
    wingsPersistence.save(settingAttribute4);

    yamlPushService.pushYamlChangeSet(
        settingAttribute1.getAccountId(), null, settingAttribute1, Type.CREATE, syncFromGit, false);
    yamlPushService.pushYamlChangeSet(
        settingAttribute2.getAccountId(), null, settingAttribute2, Type.CREATE, syncFromGit, false);
    yamlPushService.pushYamlChangeSet(
        settingAttribute3.getAccountId(), null, settingAttribute3, Type.CREATE, syncFromGit, false);
    yamlPushService.pushYamlChangeSet(
        settingAttribute4.getAccountId(), null, settingAttribute4, Type.CREATE, syncFromGit, false);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.SettingsService#getSettingAttributesByType(java.lang.String,
   * software.wings.settings.SettingValue.SettingVariableTypes)
   */
  @Override
  public List<SettingAttribute> getSettingAttributesByType(String appId, String type) {
    return getSettingAttributesByType(appId, GLOBAL_ENV_ID, type);
  }

  @Override
  public List<SettingAttribute> getFilteredSettingAttributesByType(
      String appId, String type, String currentAppId, String currentEnvId) {
    return getFilteredSettingAttributesByType(appId, GLOBAL_ENV_ID, type, currentAppId, currentEnvId);
  }

  @Override
  public List<SettingAttribute> getSettingAttributesByType(String appId, String envId, String type) {
    PageRequest<SettingAttribute> pageRequest;
    if (appId == null || appId.equals(GLOBAL_APP_ID)) {
      pageRequest = aPageRequest()
                        .addFilter("appId", EQ, GLOBAL_APP_ID)
                        .addFilter("envId", EQ, GLOBAL_ENV_ID)
                        .addFilter("value.type", EQ, type)
                        .build();
    } else {
      Application application = appService.get(appId);
      pageRequest = aPageRequest()
                        .addFilter("accountId", EQ, application.getAccountId())
                        .addFilter("envId", EQ, GLOBAL_ENV_ID)
                        .addFilter("value.type", EQ, type)
                        .build();
    }

    return wingsPersistence.query(SettingAttribute.class, pageRequest).getResponse();
  }

  @Override
  public List<SettingAttribute> getFilteredSettingAttributesByType(
      String appId, String envId, String type, String currentAppId, String currentEnvId) {
    List<SettingAttribute> settingAttributeList = getSettingAttributesByType(appId, envId, type);
    return getFilteredSettingAttributes(settingAttributeList, currentAppId, currentEnvId);
  }

  @Override
  public List<SettingAttribute> getSettingAttributesByType(String accountId, String appId, String envId, String type) {
    List<SettingAttribute> settingAttributes = new ArrayList<>();

    try (HIterator<SettingAttribute> iterator = new HIterator(wingsPersistence.createQuery(SettingAttribute.class)
                                                                  .filter(SettingAttributeKeys.accountId, accountId)
                                                                  .filter(SettingAttributeKeys.appId, appId)
                                                                  .filter(SettingAttributeKeys.envId, envId)
                                                                  .filter(VALUE_TYPE_KEY, type)
                                                                  .order(NAME_KEY)
                                                                  .fetch())) {
      while (iterator.hasNext()) {
        settingAttributes.add(iterator.next());
      }
    }

    return settingAttributes;
  }

  @Override
  public List<SettingAttribute> getGlobalSettingAttributesByType(String accountId, String type) {
    PageRequest<SettingAttribute> pageRequest =
        aPageRequest().addFilter("accountId", EQ, accountId).addFilter("value.type", EQ, type).build();
    return wingsPersistence.query(SettingAttribute.class, pageRequest).getResponse();
  }

  @Override
  public List<SettingAttribute> getFilteredGlobalSettingAttributesByType(
      String accountId, String type, String currentAppId, String currentEnvId) {
    List<SettingAttribute> settingAttributeList = getGlobalSettingAttributesByType(accountId, type);
    return getFilteredSettingAttributes(settingAttributeList, currentAppId, currentEnvId);
  }

  @Override
  public SettingValue getSettingValueById(String accountId, String id) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.accountId, accountId)
                                            .filter(SettingAttribute.ID_KEY, id)
                                            .get();
    if (settingAttribute != null) {
      return settingAttribute.getValue();
    }
    return null;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(SettingAttribute.class).filter(SettingAttributeKeys.accountId, accountId));
  }

  @Override
  public void deleteSettingAttributesByType(String accountId, String appId, String envId, String type) {
    wingsPersistence.delete(wingsPersistence.createQuery(SettingAttribute.class)
                                .filter(SettingAttributeKeys.accountId, accountId)
                                .filter("appId", appId)
                                .filter(SettingAttributeKeys.envId, envId)
                                .filter("value.type", type));
  }

  @Override
  public GitConfig fetchGitConfigFromConnectorId(String gitConnectorId) {
    if (isBlank(gitConnectorId)) {
      return null;
    }

    SettingAttribute gitSettingAttribute = get(gitConnectorId);

    if (gitSettingAttribute == null || !(gitSettingAttribute.getValue() instanceof GitConfig)) {
      throw new InvalidRequestException("Git connector not found", USER);
    }

    GitConfig gitConfig = (GitConfig) gitSettingAttribute.getValue();
    gitConfigHelperService.setSshKeySettingAttributeIfNeeded(gitConfig);
    return gitConfig;
  }

  @Override
  public String fetchAccountIdBySettingId(String settingId) {
    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.uuid, settingId)
                                            .project(SettingAttributeKeys.accountId, true)
                                            .get();
    if (settingAttribute == null) {
      throw new InvalidRequestException(format("Setting attribute %s not found", settingId), USER);
    }
    return settingAttribute.getAccountId();
  }

  @Override
  public UsageRestrictions getUsageRestrictionsForSettingId(String settingId) {
    SettingAttribute settingAttribute = get(settingId);
    if (settingAttribute == null) {
      throw new InvalidRequestException(format("Setting attribute %s not found", settingId), USER);
    }
    return getUsageRestriction(settingAttribute);
  }

  @Override
  public void pruneBySettingAttribute(String appId, String settingId) {
    // Delete all artifact stream bindings and artifact streams
    wingsPersistence.createQuery(ArtifactStream.class)
        .filter(ArtifactStreamKeys.appId, appId)
        .filter(ArtifactStreamKeys.settingId, settingId)
        .asList()
        .forEach(artifactStream -> {
          // remove artifact stream bindings
          removeArtifactStreamBindings(artifactStream);
          artifactStreamService.pruneArtifactStream(appId, artifactStream.getUuid());
          auditServiceHelper.reportDeleteForAuditing(appId, artifactStream);
        });
  }

  private void removeArtifactStreamBindings(ArtifactStream artifactStream) {
    List<ServiceVariable> serviceVariables =
        artifactStreamServiceBindingService.fetchArtifactServiceVariableByArtifactStreamId(
            artifactStream.getAccountId(), artifactStream.getUuid());
    if (isNotEmpty(serviceVariables)) {
      for (ServiceVariable serviceVariable : serviceVariables) {
        List<String> allowedList = serviceVariable.getAllowedList();
        allowedList.remove(artifactStream.getUuid());
        // TODO: remove artifact variable if allowedList is empty
        serviceVariable.setAllowedList(allowedList);
        serviceVariableService.updateWithChecks(serviceVariable.getAppId(), serviceVariable.getUuid(), serviceVariable);
      }
    }

    List<Workflow> workflows = artifactStreamServiceBindingService.listWorkflows(artifactStream.getUuid());
    if (isNotEmpty(workflows)) {
      for (Workflow workflow : workflows) {
        if (workflow.getOrchestrationWorkflow() != null
            && isNotEmpty(workflow.getOrchestrationWorkflow().getUserVariables())) {
          List<Variable> userVariables = workflow.getOrchestrationWorkflow().getUserVariables();
          List<Variable> updatedUserVariables = new ArrayList<>();
          for (Variable userVariable : userVariables) {
            String allowedValues = userVariable.getAllowedValues();
            List<String> allowedValuesList = CsvParser.parse(allowedValues);
            allowedValuesList.remove(artifactStream.getUuid());
            // TODO: remove artifact variable if allowedList is empty
            userVariable.setAllowedValues(join(",", allowedValuesList));
            updatedUserVariables.add(userVariable);
          }

          workflowService.updateUserVariables(workflow.getAppId(), workflow.getUuid(), updatedUserVariables);
          List<String> linkedArtifactStreamIds = workflow.getLinkedArtifactStreamIds();
          linkedArtifactStreamIds.remove(artifactStream.getUuid());
          workflow.setLinkedArtifactStreamIds(linkedArtifactStreamIds);
          workflowService.updateWorkflow(workflow, false);
        }
      }
    }
  }
}
