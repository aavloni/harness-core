package io.harness.cdng.executionplan;

import lombok.Getter;

public enum CDPlanCreatorType {
  EXECUTION_PHASES_PLAN_CREATOR("EXECUTION_PHASES_PLAN_CREATOR"),
  PHASE_PLAN_CREATOR("PHASE_PLAN_CREATOR"),
  ARTIFACT_PLAN_CREATOR("ARTIFACT_PLAN_CREATOR"),
  MANIFEST_PLAN_CREATOR("MANIFEST_PLAN_CREATOR"),
  SERVICE_PLAN_CREATOR("SERVICE_PLAN_CREATOR"),
  INFRA_PLAN_CREATOR("INFRA_PLAN_CREATOR");

  @Getter private final String name;

  CDPlanCreatorType(String name) {
    this.name = name;
  }
}
