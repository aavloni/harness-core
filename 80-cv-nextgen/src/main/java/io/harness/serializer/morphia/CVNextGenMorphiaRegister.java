package io.harness.serializer.morphia;

import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.statemachine.entity.AnalysisOrchestrator;
import io.harness.statemachine.entity.AnalysisStateMachine;

import java.util.Set;

public class CVNextGenMorphiaRegister implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(TimeSeriesRecord.class);
    set.add(AnalysisOrchestrator.class);
    set.add(AnalysisStateMachine.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // no classes to register
  }
}
