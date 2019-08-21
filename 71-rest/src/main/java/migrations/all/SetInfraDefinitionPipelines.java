package migrations.all;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static software.wings.utils.Validator.notNullCheck;

import com.google.inject.Inject;

import io.harness.beans.SortOrder;
import io.harness.beans.SortOrder.OrderType;
import io.harness.exception.ExceptionUtils;
import io.harness.mongo.SampleEntity.SampleEntityKeys;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;
import software.wings.beans.EntityType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateType;

import java.util.List;
@Slf4j
public class SetInfraDefinitionPipelines implements Migration {
  @Inject private PipelineService pipelineService;
  @Inject private WorkflowService workflowService;
  @Inject private AppService appService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  private static final String accountId = "zEaak-FLS425IEO7OLzMUg";

  @Override
  public void migrate() {
    logger.info("Running infra migration for pipelines.Retrieving applications for accountId: " + accountId);
    List<String> apps = appService.getAppIdsByAccountId(accountId);

    if (isEmpty(apps)) {
      logger.info("No applications found");
      return;
    }
    logger.info("Updating {} applications.", apps.size());
    for (String appId : apps) {
      migrate(appId);
    }
    // migrate("d1Z4dCeET12A2epYnEpmvw");
  }

  public void migrate(String appId) {
    SortOrder sortOrder = new SortOrder();
    sortOrder.setFieldName(SampleEntityKeys.createdAt);
    sortOrder.setOrderType(OrderType.DESC);
    List<Pipeline> pipelines =
        pipelineService
            .listPipelines(
                aPageRequest().withLimit(UNLIMITED).addFilter("appId", EQ, appId).addOrder(sortOrder).build())
            .getResponse();

    logger.info("Updating {} pipelines.", pipelines.size());
    for (Pipeline pipeline : pipelines) {
      try {
        migrate(pipeline);
      } catch (Exception e) {
        logger.error("[INFRA_MIGRATION_ERROR] Migration failed for PipelineId: " + pipeline.getUuid()
            + ExceptionUtils.getMessage(e));
      }
    }
  }

  public void migrate(Pipeline pipeline) {
    boolean modified = false;
    // Migrate each stage

    for (PipelineStage stage : pipeline.getPipelineStages()) {
      PipelineStageElement stageElement = stage.getPipelineStageElements().get(0);

      // No migration needed for approval stage. Hence continue
      if (stageElement.getType().equals(StateType.APPROVAL.name())) {
        logger.info("Approval state needs no migration");
        continue;
      }

      if (isEmpty(stageElement.getWorkflowVariables())) {
        logger.info("No workflow variables, so no migration needed");
        continue;
      }

      boolean modifiedCurrentPhase;
      try {
        modifiedCurrentPhase = migrateWorkflowVariables(pipeline, stageElement);
      } catch (Exception e) {
        logger.error(
            "[INFRA_MIGRATION_ERROR] Skipping migration.Exception in migrating workflowVariables for Pipeline: "
                + pipeline.getUuid(),
            e);
        modifiedCurrentPhase = false;
      }
      modified = modified || modifiedCurrentPhase;
    }

    if (modified) {
      try {
        pipelineService.update(pipeline);
        logger.info("--- Pipeline updated: {}, {}", pipeline.getUuid(), pipeline.getName());
        Thread.sleep(100);
      } catch (Exception e) {
        logger.error("[INFRA_MIGRATION_ERROR] Error updating pipeline " + pipeline.getUuid(), e);
      }
    }
  }

  private boolean migrateWorkflowVariables(Pipeline pipeline, PipelineStageElement stageElement) {
    String workflowId = String.valueOf(stageElement.getProperties().get("workflowId"));
    Workflow workflow = workflowService.readWorkflow(pipeline.getAppId(), workflowId);

    notNullCheck("workflow is null, workflowId: " + workflowId, workflow);
    notNullCheck("orchestrationWorkflow is null in workflow: " + workflowId, workflow.getOrchestrationWorkflow());

    if (isEmpty(workflow.getOrchestrationWorkflow().getUserVariables())) {
      logger.info(
          "[INFRA_MIGRATION_INFO] Skipping migration. Pipeline stage has workflow variables but workflow does not have userVariables.PipelineId: "
          + pipeline.getUuid() + " pipelineStageId: " + stageElement.getUuid());
      return false;
    }

    List<Variable> userVariables = workflow.getOrchestrationWorkflow().getUserVariables();
    Variable infraUserVariable = null;
    for (Variable userVariable : userVariables) {
      if (userVariable.obtainEntityType() != null
          && userVariable.obtainEntityType().equals(EntityType.INFRASTRUCTURE_MAPPING)) {
        infraUserVariable = userVariable;
        break;
      }
    }

    if (infraUserVariable == null) {
      logger.info(
          "[INFRA_MIGRATION_INFO] Pipeline stage with workflow where infraMapping not templatised. skipping migration. PipelineId: "
          + pipeline.getUuid() + " pipelineStageId: " + stageElement.getUuid());
      return false;
    }

    String infraMappingVariableName = infraUserVariable.getName();

    if (!stageElement.getWorkflowVariables().containsKey(infraMappingVariableName)) {
      // Workflow has infraMapping templatised but pipeline doesn't have infraMapping variable. Invalid pipeline
      logger.info(
          "[INFRA_MIGRATION_INFO] Workflow has infra Mapping templatised but pipeline stage does not have infra mapping variable. PipelineId: "
          + pipeline.getUuid() + " pipelineStageId: " + stageElement.getUuid());
      return false;
    }

    // new variable name
    String infraDefVariableName =
        WorkflowServiceTemplateHelper.getInfraDefVariableNameFromInfraMappingVariableName(infraMappingVariableName);
    String infraMappingId = stageElement.getWorkflowVariables().get(infraMappingVariableName);

    if (pipeline.isEnvParameterized()) {
      stageElement.getWorkflowVariables().put(infraDefVariableName, infraMappingId);
      stageElement.getWorkflowVariables().remove(infraMappingVariableName);
      return true;
    } else {
      InfrastructureMapping infrastructureMapping =
          infrastructureMappingService.get(pipeline.getAppId(), infraMappingId);
      if (infrastructureMapping == null) {
        logger.info("[INFRA_MIGRATION_INFO] Couldn't fetch infraMapping for pipeline. Pipeline:  " + pipeline.getUuid()
            + " infraMappingId: " + infraMappingId + " pipelineStageId: " + stageElement.getUuid());
        // Removing infraMapping variable as it does not have a valid infraMappingId. removing will mark workflow
        // incomplete and later user can complete the workflow
        stageElement.getWorkflowVariables().remove(infraMappingVariableName);
        return true;
      }

      String infraDefId = infrastructureMapping.getInfrastructureDefinitionId();
      if (isEmpty(infraDefId)) {
        logger.error("[INFRA_MIGRATION_ERROR]Couldn't find infraDefinition id  for pipeline. Pipeline:  "
            + pipeline.getUuid() + "infraMappingId: " + infraMappingId + " pipelineStageId: " + stageElement.getUuid());
        return false;
      }
      stageElement.getWorkflowVariables().put(infraDefVariableName, infraDefId);
      stageElement.getWorkflowVariables().remove(infraMappingVariableName);
      return true;
    }
  }
}
