/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.ecs.EcsBlueGreenRollbackResult;
import io.harness.delegate.beans.ecs.EcsBlueGreenRollbackResult.EcsBlueGreenRollbackResultBuilder;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceNotFoundException;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenRollbackCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsBlueGreenRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsBlueGreenRollbackRequest"));
    }
    EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest = (EcsBlueGreenRollbackRequest) ecsCommandRequest;

    timeoutInMillis = ecsBlueGreenRollbackRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsBlueGreenRollbackRequest.getEcsInfraConfig();

    LogCallback rollbackCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);

    try {
      rollbackCallback.saveExecutionLog(format("Rolling Back..%n%n"), LogLevel.INFO);
      AwsInternalConfig awsInternalConfig =
          awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());

      if (!ecsBlueGreenRollbackRequest.isFirstDeployment()) {
        ecsCommandTaskHelper.updateOldService(
            ecsBlueGreenRollbackRequest, awsInternalConfig, rollbackCallback, timeoutInMillis);

        rollbackCallback.saveExecutionLog(
            color(format("Old Service: %s is now stable %n%n", ecsBlueGreenRollbackRequest.getOldServiceName()),
                LogColor.White, Bold),
            LogLevel.INFO);
      }

      // Get current prod targetGroup Arn
      String currentProdTargetGroupArn = ecsCommandTaskHelper.getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
          ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
          ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn(),
          ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);
      // modify it if that's change from earlier prod targetGroup Arn
      if (!ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdTargetGroupArn().equals(
              currentProdTargetGroupArn)) {
        rollbackCallback.saveExecutionLog(
            format("Modifying ELB Prod Listener to Forward requests to Target group"
                    + " associated with old Service: %s %n,"
                    + "TargetGroup: %s",
                ecsBlueGreenRollbackRequest.getOldServiceName(),
                ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdTargetGroupArn()),
            LogLevel.INFO);
        ecsCommandTaskHelper.modifyListenerRule(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdTargetGroupArn(), awsInternalConfig,
            rollbackCallback);
        rollbackCallback.saveExecutionLog(
            color(format("Successfully updated Prod Listener %n%n"), LogColor.White, Bold), LogLevel.INFO);
      } else {
        rollbackCallback.saveExecutionLog(
            format("ELB Prod Listener: %s %n is already forwarding requests to Target group"
                    + " associated with old Service: %s %n,"
                    + "TargetGroup: %s, no need to modify it",
                ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
                ecsBlueGreenRollbackRequest.getOldServiceName(),
                ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdTargetGroupArn()),
            LogLevel.INFO);
      }

      // Get current stage targetGroup Arn
      String currentStageTargetGroupArn = ecsCommandTaskHelper.getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
          ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(),
          ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn(),
          ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);
      // modify it if that's change from earlier stage targetGroup Arn
      if (!ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageTargetGroupArn().equals(
              currentStageTargetGroupArn)) {
        rollbackCallback.saveExecutionLog(
            format("Modifying ELB Stage Listener to Forward requests to Target group"
                    + " associated with new Service: %s %n,"
                    + "TargetGroup: %s",
                ecsBlueGreenRollbackRequest.getNewServiceName(),
                ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageTargetGroupArn()),
            LogLevel.INFO);
        ecsCommandTaskHelper.modifyListenerRule(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageTargetGroupArn(), awsInternalConfig,
            rollbackCallback);
        rollbackCallback.saveExecutionLog(
            color(format("Successfully updated Stage Listener %n%n"), LogColor.White, Bold), LogLevel.INFO);
      } else {
        rollbackCallback.saveExecutionLog(
            format("ELB Stage Listener: %s is already forwarding requests to Target group"
                    + " associated with new Service: %s %n,"
                    + "TargetGroup: %s, no need to modify it",
                ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(),
                ecsBlueGreenRollbackRequest.getNewServiceName(),
                ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageTargetGroupArn()),
            LogLevel.INFO);
      }

      if (!ecsBlueGreenRollbackRequest.isFirstDeployment()) {
        rollbackCallback.saveExecutionLog(
            format("Updating tag of old service: %s", ecsBlueGreenRollbackRequest.getOldServiceName()));
        // update service tag of old service with blue version
        ecsCommandTaskHelper.updateTag(ecsBlueGreenRollbackRequest.getOldServiceName(), ecsInfraConfig,
            EcsCommandTaskNGHelper.BG_BLUE, awsInternalConfig, rollbackCallback);

        rollbackCallback.saveExecutionLog(
            color(format("Successfully updated tag %n%n"), LogColor.White, LogWeight.Bold), LogLevel.INFO);
      }

      rollbackCallback.saveExecutionLog(
          format("Updating tag of new service: %s", ecsBlueGreenRollbackRequest.getNewServiceName()));
      // update service tag of new service with green version
      ecsCommandTaskHelper.updateTag(ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig,
          EcsCommandTaskNGHelper.BG_GREEN, awsInternalConfig, rollbackCallback);

      rollbackCallback.saveExecutionLog(
          color(format("Successfully updated tag %n%n"), LogColor.White, LogWeight.Bold), LogLevel.INFO);

      if (ecsBlueGreenRollbackRequest.isGreenServiceRollbackEnabled()) {
        rollbackGreenService(ecsBlueGreenRollbackRequest, rollbackCallback);
      } else {
        rollbackCallback.saveExecutionLog(
            format("Removing green service:  %s scaling policies", ecsBlueGreenRollbackRequest.getNewServiceName()));
        // deleting scaling policies for new service
        ecsCommandTaskHelper.deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(),
            ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            rollbackCallback);

        rollbackCallback.saveExecutionLog(
            format("Removing green service:  %s scalable targets", ecsBlueGreenRollbackRequest.getNewServiceName()));
        // de-registering scalable target for new service
        ecsCommandTaskHelper.deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(),
            ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            rollbackCallback);

        rollbackCallback.saveExecutionLog(format(
            "Downsizing green service:  %s with zero desired count", ecsBlueGreenRollbackRequest.getNewServiceName()));
        // downsize new service desired count to zero
        UpdateServiceResponse updateServiceResponse = ecsCommandTaskHelper.updateDesiredCount(
            ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig, awsInternalConfig, 0);

        if (updateServiceResponse.service() != null) {
          rollbackCallback.saveExecutionLog(format("Current desired count for green service:  %s is %s",
              ecsBlueGreenRollbackRequest.getNewServiceName(), updateServiceResponse.service().desiredCount()));
        }
        rollbackCallback.saveExecutionLog("Waiting 30s for downsize to complete green service to synchronize");
      }

      EcsBlueGreenRollbackResultBuilder ecsBlueGreenRollbackResultBuilder =
          EcsBlueGreenRollbackResult.builder()
              .region(ecsBlueGreenRollbackRequest.getEcsInfraConfig().getRegion())
              .loadBalancer(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer())
              .prodListenerArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn())
              .prodListenerRuleArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn())
              .stageListenerArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn())
              .stageListenerRuleArn(ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn())
              .infrastructureKey(ecsBlueGreenRollbackRequest.getEcsInfraConfig().getInfraStructureKey());

      if (ecsBlueGreenRollbackRequest.isFirstDeployment()) {
        // first deployment case
        ecsBlueGreenRollbackResultBuilder.isFirstDeployment(true);
      } else {
        ecsBlueGreenRollbackResultBuilder.ecsTasks(
            ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
                ecsBlueGreenRollbackRequest.getOldServiceName(), ecsInfraConfig.getRegion()));
      }
      ecsBlueGreenRollbackResultBuilder.prodTargetGroupArn(
          ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdTargetGroupArn());
      ecsBlueGreenRollbackResultBuilder.stageTargetGroupArn(
          ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageTargetGroupArn());
      rollbackCallback.saveExecutionLog(color(format("Rollback Successful. %n"), LogColor.Green, LogWeight.Bold),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
      return EcsBlueGreenRollbackResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .ecsBlueGreenRollbackResult(ecsBlueGreenRollbackResultBuilder.build())
          .build();
    } catch (Exception e) {
      rollbackCallback.saveExecutionLog(color(format("Rollback Failed. %n"), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }
  private void rollbackGreenService(
      EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest, LogCallback rollbackCallback) {
    if (ecsBlueGreenRollbackRequest.isGreenServiceExisted()) {
      // if green service existed before deployment, do rollback
      CreateServiceRequest.Builder createServiceRequestBuilder =
          ecsCommandTaskHelper.parseYamlAsObject(ecsBlueGreenRollbackRequest.getNewServiceRequestBuilderString(),
              CreateServiceRequest.serializableBuilderClass());
      Integer desiredCount = ecsCommandTaskHelper.getDesiredCountOfServiceForRollback(ecsInfraConfig,
          createServiceRequestBuilder.build().desiredCount(), createServiceRequestBuilder.build().serviceName());
      // replace cluster and desired count
      CreateServiceRequest createServiceRequest =
          createServiceRequestBuilder.cluster(ecsInfraConfig.getCluster()).desiredCount(desiredCount).build();
      rollbackCallback.saveExecutionLog(format("Rolling back green service: %s with desired count: %s",
          ecsBlueGreenRollbackRequest.getNewServiceName(), desiredCount));
      // if service exists create service, otherwise update service
      Optional<Service> optionalService = ecsCommandTaskHelper.describeService(createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      if (optionalService.isPresent() && ecsCommandTaskHelper.isServiceActive(optionalService.get())) {
        Service service = optionalService.get();
        ecsCommandTaskHelper.deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(),
            ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), rollbackCallback);
        ecsCommandTaskHelper.deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(),
            ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), rollbackCallback);

        UpdateServiceRequest updateServiceRequest =
            EcsMapper.createServiceRequestToUpdateServiceRequest(createServiceRequest, false);

        rollbackCallback.saveExecutionLog(
            format("Updating Green Service %s with task definition %s and desired count %s %n",
                updateServiceRequest.service(), updateServiceRequest.taskDefinition(),
                updateServiceRequest.desiredCount()),
            LogLevel.INFO);
        UpdateServiceResponse updateServiceResponse = ecsCommandTaskHelper.updateService(
            updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

        rollbackCallback.saveExecutionLog(format("Updated Service %s with Arn %s %n", updateServiceRequest.service(),
                                              updateServiceResponse.service().serviceArn()),
            LogLevel.INFO);

        ecsCommandTaskHelper.registerScalableTargets(
            ecsBlueGreenRollbackRequest.getNewServiceScalableTargetRequestBuilderStrings(),
            ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
            ecsInfraConfig.getRegion(), rollbackCallback);

        ecsCommandTaskHelper.attachScalingPolicies(
            ecsBlueGreenRollbackRequest.getNewServiceScalingPolicyRequestBuilderStrings(),
            ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
            ecsInfraConfig.getRegion(), rollbackCallback);
      }
    } else {
      // if green service doesn't exist before deployment, delete it
      String serviceName = ecsBlueGreenRollbackRequest.getNewServiceName();

      rollbackCallback.saveExecutionLog(format("Deleting green service %s..", serviceName), LogLevel.INFO);

      try {
        ecsCommandTaskHelper.deleteService(
            serviceName, ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
      } catch (Exception e) {
        if (e.getCause() instanceof ServiceNotFoundException) {
          rollbackCallback.saveExecutionLog(format("service %s doesn't exist, so "
                                                    + "skipping deletion of service",
                                                serviceName),
              LogLevel.INFO);
        } else {
          throw e;
        }
      }
    }
  }
}
