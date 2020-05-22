package io.harness.beans.steps;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.beans.script.ScriptInfo;
import io.harness.state.StepType;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;

@JsonTypeName("CUSTOM")
@Data
@Value
@Builder
public class CustomStepInfo implements StepInfo {
  @NotNull private StepInfoType type = StepInfoType.CUSTOM;
  @NotNull public static final StepType stateType = StepType.builder().type(StepInfoType.CUSTOM.name()).build();

  @NotEmpty private String identifier;
  private List<ScriptInfo> scriptInfos = new ArrayList<>();

  @Override
  public StepInfoType getType() {
    return type;
  }

  @Override
  public StepType getStateType() {
    return stateType;
  }

  @Override
  public String getStepIdentifier() {
    return identifier;
  }
}
