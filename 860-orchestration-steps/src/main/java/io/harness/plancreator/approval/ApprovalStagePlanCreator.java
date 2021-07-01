package io.harness.plancreator.approval;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.stages.GenericStagePlanCreator;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.utils.CommonPlanCreatorUtils;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.steps.approval.stage.ApprovalStageSpecParameters;
import io.harness.steps.approval.stage.ApprovalStageStep;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ApprovalStagePlanCreator extends GenericStagePlanCreator {
  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton("Approval");
  }

  @Override
  public StepType getStepType(StageElementConfig stageElementConfig) {
    return ApprovalStageStep.STEP_TYPE;
  }

  @Override
  public SpecParameters getSpecParameters(
      String childNodeId, PlanCreationContext ctx, StageElementConfig stageElementConfig) {
    return ApprovalStageSpecParameters.getStepParameters(childNodeId);
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StageElementConfig field) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();

    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));

    // Add dependency for execution
    YamlField executionField = specField.getNode().getField(YAMLFieldNameConstants.EXECUTION);
    if (executionField == null) {
      throw new InvalidRequestException("Execution section is required in Approval stage");
    }
    dependenciesNodeMap.put(executionField.getNode().getUuid(), executionField);

    // Adding Spec node
    PlanNode specPlanNode =
        CommonPlanCreatorUtils.getSpecPlanNode(specField.getNode().getUuid(), executionField.getNode().getUuid());
    planCreationResponseMap.put(
        specPlanNode.getUuid(), PlanCreationResponse.builder().node(specPlanNode.getUuid(), specPlanNode).build());

    planCreationResponseMap.put(executionField.getNode().getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap))
            .build());
    return planCreationResponseMap;
  }
}
