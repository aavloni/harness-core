package io.harness.states;

import io.harness.beans.steps.stepinfo.PublishStepInfo;
import io.harness.pms.steps.StepType;

public class PublishStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = PublishStepInfo.typeInfo.getStepType();
}
