package software.wings.service.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.sshd.common.util.GenericUtils.isEmpty;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;
import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.beans.ErrorCodes.INVALID_ARGUMENT;
import static software.wings.beans.History.Builder.aHistory;
import static software.wings.beans.InformationNotification.Builder.anInformationNotification;
import static software.wings.beans.Setup.SetupStatus.INCOMPLETE;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.common.NotificationMessageResolver.ENTITY_CREATE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.ENTITY_DELETE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.getDecoratedNotificationMessage;

import com.google.common.collect.ImmutableMap;

import de.danielbechler.diff.ObjectDifferBuilder;
import de.danielbechler.diff.node.DiffNode;
import de.danielbechler.diff.path.NodePath;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion;
import software.wings.beans.EntityVersion.ChangeType;
import software.wings.beans.EventType;
import software.wings.beans.Graph;
import software.wings.beans.Service;
import software.wings.beans.Setup.SetupStatus;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ServiceCommand;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.exception.WingsException;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.CommandService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.HistoryService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.SetupService;
import software.wings.stencils.DataProvider;
import software.wings.stencils.Stencil;
import software.wings.stencils.StencilPostProcessor;
import software.wings.utils.Validator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by anubhaw on 3/25/16.
 */
@ValidateOnExecution
@Singleton
public class ServiceResourceServiceImpl implements ServiceResourceService, DataProvider {
  private final Logger logger = LoggerFactory.getLogger(ServiceResourceServiceImpl.class);

  @Inject private WingsPersistence wingsPersistence;
  @Inject private ConfigService configService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private ExecutorService executorService;
  @Inject private StencilPostProcessor stencilPostProcessor;
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private ActivityService activityService;
  @Inject private SetupService setupService;
  @Inject private NotificationService notificationService;
  @Inject private HistoryService historyService;
  @Inject private EntityVersionService entityVersionService;
  @Inject private CommandService commandService;

  /**
   * {@inheritDoc}
   */
  @Override
  public PageResponse<Service> list(PageRequest<Service> request) {
    PageResponse<Service> pageResponse = wingsPersistence.query(Service.class, request);
    pageResponse.getResponse().forEach(service -> {
      service.setConfigFiles(
          configService.getConfigFilesForEntity(service.getAppId(), DEFAULT_TEMPLATE_ID, service.getUuid()));
    });
    return pageResponse;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Service save(Service service) {
    Service savedService = wingsPersistence.saveAndGet(Service.class, service);
    savedService = addDefaultCommands(savedService);
    serviceTemplateService.createDefaultTemplatesByService(savedService);
    notificationService.sendNotificationAsync(
        anInformationNotification()
            .withAppId(savedService.getAppId())
            .withDisplayText(getDecoratedNotificationMessage(ENTITY_CREATE_NOTIFICATION,
                ImmutableMap.of("ENTITY_TYPE", "Service", "ENTITY_NAME", savedService.getName())))
            .build());
    historyService.createAsync(aHistory()
                                   .withEventType(EventType.CREATED)
                                   .withAppId(service.getAppId())
                                   .withEntityType(EntityType.SERVICE)
                                   .withEntityId(service.getUuid())
                                   .withEntityName(service.getName())
                                   .withEntityNewValue(service)
                                   .withShortDescription("Service " + service.getName() + " created")
                                   .withTitle("Service " + service.getName() + " created")
                                   .build());
    return savedService;
  }

  private Service addDefaultCommands(Service service) {
    List<Graph> commands = emptyList();
    if (service.getAppContainer() != null && service.getAppContainer().getFamily() != null) {
      commands = service.getAppContainer().getFamily().getDefaultCommands(
          service.getArtifactType(), service.getAppContainer());
    } else if (service.getArtifactType() != null) {
      commands = service.getArtifactType().getDefaultCommands();
    }

    Service serviceToReturn = service;
    for (Graph command : commands) {
      serviceToReturn = addCommand(service.getAppId(), service.getUuid(),
          aServiceCommand().withCommand(aCommand().withGraph(command).build()).build());
    }

    return serviceToReturn;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Service update(Service service) {
    Service savedService = wingsPersistence.get(Service.class, service.getAppId(), service.getUuid());
    wingsPersistence.updateFields(Service.class, service.getUuid(),
        ImmutableMap.of("name", service.getName().trim(), "description", service.getDescription(), "artifactType",
            service.getArtifactType(), "appContainer", service.getAppContainer()));
    if (!savedService.getName().equals(service.getName())) {
      executorService.submit(()
                                 -> serviceTemplateService.updateDefaultServiceTemplateName(service.getAppId(),
                                     service.getUuid(), savedService.getName(), service.getName().trim()));
    }
    return wingsPersistence.get(Service.class, service.getAppId(), service.getUuid());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Service get(String appId, String serviceId) {
    Service service = wingsPersistence.get(Service.class, appId, serviceId);
    if (service == null) {
      throw new WingsException(INVALID_ARGUMENT, "args", "Service doesn't exist");
    }

    service.setConfigFiles(configService.getConfigFilesForEntity(appId, DEFAULT_TEMPLATE_ID, service.getUuid()));
    service.setLastDeploymentActivity(activityService.getLastActivityForService(appId, serviceId));
    service.setLastProdDeploymentActivity(activityService.getLastProductionActivityForService(appId, serviceId));
    service.getServiceCommands().forEach(serviceCommand
        -> serviceCommand.setCommand(
            commandService.getCommand(serviceCommand.getUuid(), serviceCommand.getDefaultVersion())));
    return service;
  }

  @Override
  public boolean exist(@NotEmpty String appId, @NotEmpty String serviceId) {
    return wingsPersistence.createQuery(Service.class)
               .field("appId")
               .equal(appId)
               .field(ID_KEY)
               .equal(serviceId)
               .getKey()
        != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void delete(String appId, String serviceId) {
    Service service = wingsPersistence.get(Service.class, appId, serviceId);
    if (service == null) {
      throw new WingsException(INVALID_ARGUMENT, "args", "Service doesn't exist");
    }

    boolean deleted = wingsPersistence.delete(Service.class, serviceId);
    if (deleted) {
      executorService.submit(() -> {
        notificationService.sendNotificationAsync(
            anInformationNotification()
                .withAppId(service.getAppId())
                .withDisplayText(getDecoratedNotificationMessage(ENTITY_DELETE_NOTIFICATION,
                    ImmutableMap.of("ENTITY_TYPE", "Service", "ENTITY_NAME", service.getName())))
                .build());
        historyService.createAsync(aHistory()
                                       .withEventType(EventType.DELETED)
                                       .withAppId(service.getAppId())
                                       .withEntityType(EntityType.SERVICE)
                                       .withEntityId(service.getUuid())
                                       .withEntityName(service.getName())
                                       .withEntityNewValue(service)
                                       .withShortDescription("Service " + service.getName() + " created")
                                       .withTitle("Service " + service.getName() + " created")
                                       .build());
        serviceTemplateService.deleteByService(appId, serviceId);
        configService.deleteByEntityId(appId, DEFAULT_TEMPLATE_ID, serviceId);
        serviceVariableService.deleteByEntityId(appId, serviceId);
      });
    }
  }

  @Override
  public void deleteByApp(String appId) {
    wingsPersistence.createQuery(Service.class)
        .field("appId")
        .equal(appId)
        .asList()
        .forEach(service -> delete(appId, service.getUuid()));
  }

  @Override
  public List<Service> findServicesByApp(String appId) {
    return wingsPersistence.createQuery(Service.class).field("appId").equal(appId).asList();
  }

  @Override
  public Service get(String appId, String serviceId, SetupStatus status) {
    Service service = get(appId, serviceId);
    if (status == INCOMPLETE) {
      service.setSetup(setupService.getServiceSetupStatus(service));
    }
    return service;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Service addCommand(String appId, String serviceId, ServiceCommand serviceCommand) {
    Service service = wingsPersistence.get(Service.class, appId, serviceId);
    Validator.notNullCheck("service", service);

    if (!serviceCommand.getCommand().getGraph().isLinear()) {
      throw new IllegalArgumentException("Graph is not a pipeline");
    }

    serviceCommand.setDefaultVersion(1);
    serviceCommand.setServiceId(serviceId);
    serviceCommand.setAppId(appId);
    serviceCommand.setName(serviceCommand.getCommand().getGraph().getGraphName());

    serviceCommand = wingsPersistence.saveAndGet(ServiceCommand.class, serviceCommand);
    entityVersionService.newEntityVersion(
        appId, EntityType.COMMAND, serviceCommand.getUuid(), serviceCommand.getName(), ChangeType.CREATED);

    Command command = serviceCommand.getCommand();
    command.transformGraph();
    command.setVersion(1L);
    command.setOriginEntityId(serviceCommand.getUuid());

    commandService.save(command);

    service.getServiceCommands().add(serviceCommand);

    wingsPersistence.save(service);
    return get(appId, serviceId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Service deleteCommand(String appId, String serviceId, String commandId) {
    Service service = wingsPersistence.get(Service.class, appId, serviceId);
    Validator.notNullCheck("service", service);

    wingsPersistence.update(
        wingsPersistence.createQuery(Service.class).field(ID_KEY).equal(serviceId).field("appId").equal(appId),
        wingsPersistence.createUpdateOperations(Service.class).removeAll("serviceCommands", commandId));

    return get(appId, serviceId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Service updateCommand(String appId, String serviceId, ServiceCommand serviceCommand) {
    Service service = wingsPersistence.get(Service.class, appId, serviceId);
    Validator.notNullCheck("service", service);

    if (!serviceCommand.getCommand().getGraph().isLinear()) {
      throw new IllegalArgumentException("Graph is not a pipeline");
    }

    EntityVersion lastEntityVersion =
        entityVersionService.lastEntityVersion(appId, EntityType.COMMAND, serviceCommand.getUuid());
    Command command = serviceCommand.getCommand();
    command.transformGraph();
    command.setOriginEntityId(serviceCommand.getUuid());

    Command oldCommand = commandService.getCommand(serviceCommand.getUuid(), lastEntityVersion.getVersion());

    DiffNode commandUnitDiff =
        ObjectDifferBuilder.buildDefault().compare(command.getCommandUnits(), oldCommand.getCommandUnits());
    ObjectDifferBuilder builder = ObjectDifferBuilder.startBuilding();
    builder.inclusion().exclude().node(NodePath.with("linearGraphIterator"));
    DiffNode graphDiff = builder.build().compare(command.getGraph(), oldCommand.getGraph());

    if (commandUnitDiff.isChanged()) {
      commandUnitDiff.visit((node, visit) -> {
        final Object baseValue = node.canonicalGet(oldCommand.getCommandUnits());
        final Object workingValue = node.canonicalGet(command.getCommandUnits());
        if (node.isChanged()) {
          logger.debug("{} changed from [{}] to [{}]", node.getPath(), baseValue, workingValue);
        }
      });
      EntityVersion entityVersion = entityVersionService.newEntityVersion(
          appId, EntityType.COMMAND, serviceCommand.getUuid(), serviceCommand.getName(), ChangeType.UPDATED);
      command.setVersion(Long.valueOf(entityVersion.getVersion().intValue()));

      commandService.save(command);

      if (serviceCommand.getSetAsDefault()) {
        wingsPersistence.update(
            wingsPersistence.createQuery(ServiceCommand.class).field(ID_KEY).equal(serviceCommand.getUuid()),
            wingsPersistence.createUpdateOperations(ServiceCommand.class)
                .set("defaultVersion", entityVersion.getVersion()));
      }
    } else if (graphDiff.isChanged()) {
      oldCommand.setGraph(command.getGraph());
      commandService.update(oldCommand);
    }

    return get(appId, serviceId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ServiceCommand getCommandByName(
      @NotEmpty String appId, @NotEmpty String serviceId, @NotEmpty String commandName) {
    Service service = get(appId, serviceId);
    return service.getServiceCommands()
        .stream()
        .filter(command -> equalsIgnoreCase(commandName, command.getName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public ServiceCommand getCommandByName(
      @NotEmpty String appId, @NotEmpty String serviceId, @NotEmpty String envId, @NotEmpty String commandName) {
    Service service = get(appId, serviceId);
    ServiceCommand serviceCommand = service.getServiceCommands()
                                        .stream()
                                        .filter(command -> equalsIgnoreCase(commandName, command.getName()))
                                        .findFirst()
                                        .orElse(null);
    if (serviceCommand != null && serviceCommand.getEnvIdVersionMap().containsKey(envId)) {
      serviceCommand.setCommand(commandService.getCommand(
          serviceCommand.getUuid(), serviceCommand.getEnvIdVersionMap().get(envId).getVersion()));
    }
    return serviceCommand;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ServiceCommand getCommandByNameAndVersion(
      @NotEmpty String appId, @NotEmpty String serviceId, @NotEmpty String commandName, int version) {
    Service service = get(appId, serviceId);
    ServiceCommand command = service.getServiceCommands()
                                 .stream()
                                 .filter(serviceCommand -> equalsIgnoreCase(commandName, serviceCommand.getName()))
                                 .findFirst()
                                 .get();
    command.setCommand(commandService.getCommand(command.getUuid(), version));
    return command;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Stencil> getCommandStencils(@NotEmpty String appId, @NotEmpty String serviceId, String commandName) {
    return stencilPostProcessor.postProcess(Arrays.asList(CommandUnitType.values()), appId, serviceId, commandName);
  }

  @Override
  public Map<String, String> getData(String appId, String... params) {
    Service service = get(appId, params[0]);
    if (isEmpty(service.getServiceCommands())) {
      return emptyMap();
    } else {
      return service.getServiceCommands()
          .stream()
          .filter(command -> !StringUtils.equals(command.getName(), params[1]))
          .collect(toMap(ServiceCommand::getName, ServiceCommand::getName));
    }
  }
}
