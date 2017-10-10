package software.wings.service.impl;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.reflection.Whitebox.setInternalState;
import static software.wings.beans.JenkinsConfig.Builder.aJenkinsConfig;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import software.wings.WingsBaseTest;
import software.wings.beans.DelegateTask.SyncTaskContext;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.Category;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.integration.BaseIntegrationTest;
import software.wings.rules.RealMongo;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.JenkinsBuildService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;

/**
 * Created by rsingh on 10/9/17.
 */
public class BuildSourceServiceTest extends WingsBaseTest {
  private String accountId;
  private String appId;
  private SettingAttribute jenkinsSettingAttribute;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Inject private BuildSourceService buildSourceService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private JenkinsBuildService jenkinsBuildService;

  @Before
  public void setup() {
    accountId = UUID.randomUUID().toString();
    appId = UUID.randomUUID().toString();
    MockitoAnnotations.initMocks(this);
    when(delegateProxyFactory.get(Mockito.anyObject(), Mockito.any(SyncTaskContext.class)))
        .thenReturn(jenkinsBuildService);
    setInternalState(buildSourceService, "delegateProxyFactory", delegateProxyFactory);
    jenkinsSettingAttribute = aSettingAttribute()
                                  .withName(BaseIntegrationTest.HARNESS_JENKINS)
                                  .withCategory(Category.CONNECTOR)
                                  .withAccountId(accountId)
                                  .withValue(aJenkinsConfig()
                                                 .withAccountId(accountId)
                                                 .withJenkinsUrl("https://jenkins.wings.software")
                                                 .withUsername("wingsbuild")
                                                 .withPassword("06b13aea6f5f13ec69577689a899bbaad69eeb2f".toCharArray())
                                                 .build())
                                  .build();
    wingsPersistence.save(jenkinsSettingAttribute);
  }

  @Test
  @RealMongo
  public void getJenkinsJob() {
    Set<JobDetails> jobs = buildSourceService.getJobs(appId, jenkinsSettingAttribute.getUuid(), null);
    assertTrue(jobs.size() > 0);
  }

  @Test
  @RealMongo
  public void getJenkinsPlans() {
    Map<String, String> plans =
        buildSourceService.getPlans(appId, jenkinsSettingAttribute.getUuid(), ArtifactStreamType.JENKINS.name());
    assertTrue(plans.size() > 0);
  }

  @Test
  @RealMongo
  public void getArtifactPaths() {
    Set<String> artifactPaths = buildSourceService.getArtifactPaths(
        appId, "todolist_war", jenkinsSettingAttribute.getUuid(), null, ArtifactStreamType.JENKINS.name());
    assertTrue(artifactPaths.size() > 0);
    assertTrue(artifactPaths.contains("target/todolist.war"));
  }
}
