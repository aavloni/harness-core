/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helpers;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expressions.InputsExpressionEvaluator;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class InputSetMergeHelperV1 {
  public String mergeInputSetIntoPipelineYaml(JsonNode inputSetJsonNode, JsonNode pipelineJsonNode) {
    if (EmptyPredicate.isEmpty(inputSetJsonNode)) {
      return YamlUtils.writeYamlString(pipelineJsonNode);
    }
    pipelineJsonNode = MergeHelper.mergeOptionsRuntimeInput(pipelineJsonNode, inputSetJsonNode);
    EngineExpressionEvaluator evaluator = new InputsExpressionEvaluator(inputSetJsonNode, pipelineJsonNode);
    return YamlUtils.writeYamlString(
        (JsonNode) evaluator.resolve(pipelineJsonNode, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
  }
}
