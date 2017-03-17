package software.wings.service;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static software.wings.beans.Activity.Builder.anActivity;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.ApprovalNotification.Builder.anApprovalNotification;
import static software.wings.beans.ElementExecutionSummary.ElementExecutionSummaryBuilder.anElementExecutionSummary;
import static software.wings.beans.InformationNotification.Builder.anInformationNotification;
import static software.wings.beans.WorkflowExecution.WorkflowExecutionBuilder.aWorkflowExecution;
import static software.wings.beans.stats.ActivityStatusAggregation.Builder.anActivityStatusAggregation;
import static software.wings.beans.stats.AppKeyStatistics.AppKeyStatsBreakdown.Builder.anAppKeyStatistics;
import static software.wings.beans.stats.NotificationCount.Builder.aNotificationCount;
import static software.wings.beans.stats.TopConsumer.Builder.aTopConsumer;
import static software.wings.dl.PageResponse.Builder.aPageResponse;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mongodb.morphia.aggregation.AggregationPipeline;
import org.mongodb.morphia.aggregation.Group;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;
import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.Environment.EnvironmentType;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.stats.ActivityStatusAggregation;
import software.wings.beans.stats.ActivityStatusAggregation.StatusCount;
import software.wings.beans.stats.AppKeyStatistics;
import software.wings.beans.stats.DeploymentStatistics;
import software.wings.beans.stats.DeploymentStatistics.AggregatedDayStats;
import software.wings.beans.stats.DeploymentStatistics.AggregatedDayStats.DayStat;
import software.wings.beans.stats.NotificationCount;
import software.wings.beans.stats.TopConsumersStatistics;
import software.wings.beans.stats.UserStatistics;
import software.wings.beans.stats.UserStatistics.AppDeployment;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.StatisticsService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;

/**
 * Created by anubhaw on 2/24/17.
 */

public class StatisticsServiceTest extends WingsBaseTest {
  @Mock private AppService appService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private WingsPersistence wingsPersistence;
  @Mock private UserService userService;
  @Mock private ExecutorService executorService;
  @Mock private NotificationService notificationService;
  @Mock private ActivityService activityService;

  @Inject @InjectMocks private StatisticsService statisticsService;

  @Mock private Query<Activity> activityQuery;
  @Mock private FieldEnd activityQueryFieldEnd;
  @Mock private AggregationPipeline aggregationPipeline;

  @Before
  public void setUp() throws Exception {
    when(wingsPersistence.createQuery(Activity.class)).thenReturn(activityQuery);
    when(activityQuery.field(any())).thenReturn(activityQueryFieldEnd);
    when(activityQueryFieldEnd.in(any())).thenReturn(activityQuery);
    when(activityQueryFieldEnd.greaterThanOrEq(any())).thenReturn(activityQuery);
    when(activityQueryFieldEnd.hasAnyOf(any())).thenReturn(activityQuery);
    when(activityQuery.retrievedFields(anyBoolean(), anyVararg())).thenReturn(activityQuery);

    when(wingsPersistence.getDatastore().createAggregation(Activity.class)).thenReturn(aggregationPipeline);
    when(aggregationPipeline.match(any(Query.class))).thenReturn(aggregationPipeline);
    when(aggregationPipeline.group(anyList(), any(Group.class))).thenReturn(aggregationPipeline);
    when(aggregationPipeline.group(anyString(), any(Group.class))).thenReturn(aggregationPipeline);
  }

  @Test
  public void shouldGetTopConsumers() {
    when(appService.list(any(PageRequest.class), eq(false), eq(0), eq(0)))
        .thenReturn(aPageResponse().withResponse(asList(anApplication().withUuid(APP_ID).build())).build());
    when(aggregationPipeline.aggregate(ActivityStatusAggregation.class))
        .thenReturn(asList(anActivityStatusAggregation()
                               .withAppId(APP_ID)
                               .withStatus(asList(new StatusCount(ExecutionStatus.SUCCESS, 5),
                                   new StatusCount(ExecutionStatus.FAILED, 5)))
                               .build())
                        .iterator());
    TopConsumersStatistics topConsumers = (TopConsumersStatistics) statisticsService.getTopConsumers(ACCOUNT_ID);
    assertThat(topConsumers.getTopConsumers())
        .hasSize(1)
        .containsExactly(aTopConsumer()
                             .withAppId(APP_ID)
                             .withSuccessfulActivityCount(5)
                             .withFailedActivityCount(5)
                             .withTotalCount(10)
                             .build());
  }

  @Test
  public void shouldGetApplicationKeyStats() {
    when(activityQuery.asList())
        .thenReturn(asList(anActivity()
                               .withAppId(APP_ID)
                               .withArtifactId(ARTIFACT_ID)
                               .withWorkflowExecutionId(WORKFLOW_EXECUTION_ID)
                               .withEnvironmentType(EnvironmentType.PROD)
                               .build(),
            anActivity()
                .withAppId(APP_ID)
                .withArtifactId(ARTIFACT_ID)
                .withWorkflowExecutionId(WORKFLOW_EXECUTION_ID)
                .withEnvironmentType(EnvironmentType.NON_PROD)
                .build()));
    Map<String, AppKeyStatistics> applicationKeyStats = statisticsService.getApplicationKeyStats(asList(APP_ID), 10);
    AppKeyStatistics appKeyStatistics = new AppKeyStatistics();
    appKeyStatistics.setStatsMap(ImmutableMap.of(EnvironmentType.PROD,
        anAppKeyStatistics().withArtifactCount(1).withDeploymentCount(1).withInstanceCount(1).build(),
        EnvironmentType.NON_PROD,
        anAppKeyStatistics().withArtifactCount(1).withDeploymentCount(1).withInstanceCount(1).build(),
        EnvironmentType.ALL,
        anAppKeyStatistics().withArtifactCount(2).withDeploymentCount(2).withInstanceCount(2).build()));
    assertThat(applicationKeyStats).hasSize(1).containsOnlyKeys(APP_ID);
    assertThat(applicationKeyStats.get(APP_ID)).isEqualTo(appKeyStatistics);
  }

  @Test
  public void shouldGetUserStats() {
    long statusFetchedOn = LocalDate.now(ZoneId.systemDefault())
                               .minus(0, ChronoUnit.DAYS)
                               .atStartOfDay(ZoneId.systemDefault())
                               .toInstant()
                               .toEpochMilli();

    UserThreadLocal.set(User.Builder.anUser().withStatsFetchedOn(statusFetchedOn).build());

    List<WorkflowExecution> executions = asList(aWorkflowExecution()
                                                    .withAppId(APP_ID)
                                                    .withAppName(APP_NAME)
                                                    .withEnvType(EnvironmentType.PROD)
                                                    .withStatus(ExecutionStatus.SUCCESS)
                                                    .build(),
        aWorkflowExecution()
            .withAppId(APP_ID)
            .withAppName(APP_NAME)
            .withEnvType(EnvironmentType.NON_PROD)
            .withStatus(ExecutionStatus.FAILED)
            .build());

    when(workflowExecutionService.listExecutions(any(PageRequest.class), eq(false), eq(false), eq(false)))
        .thenReturn(aPageResponse().withResponse(executions).build());

    when(appService.list(any(PageRequest.class), eq(false), eq(0), eq(0)))
        .thenReturn(aPageResponse().withResponse(asList(anApplication().withUuid(APP_ID).build())).build());

    UserStatistics userStats = statisticsService.getUserStats(ACCOUNT_ID);
    assertThat(userStats.getDeploymentCount()).isEqualTo(2);
    assertThat(userStats.getLastFetchedOn()).isEqualTo(statusFetchedOn);
    assertThat(userStats.getAppDeployments())
        .extracting(
            AppDeployment::getAppId, AppDeployment::getAppName, appDeployment -> appDeployment.getDeployments().size())
        .containsExactly(tuple(APP_ID, APP_NAME, 2));
    UserThreadLocal.unset();
  }

  @Test
  @Ignore
  public void shouldGetDeploymentStatistics() {
    long endEpoch = LocalDate.now(ZoneId.systemDefault())
                        .minus(0, ChronoUnit.DAYS)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
    long startEpoch = LocalDate.now(ZoneId.systemDefault())
                          .minus(29, ChronoUnit.DAYS)
                          .atStartOfDay(ZoneId.systemDefault())
                          .toInstant()
                          .toEpochMilli();

    List<ElementExecutionSummary> serviceExecutionSummaries = asList(
        anElementExecutionSummary().withInstanceStatusSummaries(asList(anInstanceStatusSummary().build())).build());

    List<WorkflowExecution> executions = asList(aWorkflowExecution()
                                                    .withAppId(APP_ID)
                                                    .withEnvType(EnvironmentType.PROD)
                                                    .withStatus(ExecutionStatus.SUCCESS)
                                                    .withServiceExecutionSummaries(serviceExecutionSummaries)
                                                    .withCreatedAt(endEpoch)
                                                    .build(),
        aWorkflowExecution()
            .withAppId(APP_ID)
            .withEnvType(EnvironmentType.PROD)
            .withStatus(ExecutionStatus.SUCCESS)
            .withServiceExecutionSummaries(serviceExecutionSummaries)
            .withCreatedAt(endEpoch)
            .build(),
        aWorkflowExecution()
            .withAppId(APP_ID)
            .withEnvType(EnvironmentType.NON_PROD)
            .withStatus(ExecutionStatus.FAILED)
            .withServiceExecutionSummaries(serviceExecutionSummaries)
            .withCreatedAt(startEpoch)
            .build(),
        aWorkflowExecution()
            .withAppId(APP_ID)
            .withEnvType(EnvironmentType.NON_PROD)
            .withStatus(ExecutionStatus.FAILED)
            .withServiceExecutionSummaries(serviceExecutionSummaries)
            .withCreatedAt(startEpoch)
            .build());

    when(workflowExecutionService.listExecutions(any(PageRequest.class), eq(false), eq(false), eq(false)))
        .thenReturn(aPageResponse().withResponse(executions).build());
    DeploymentStatistics deploymentStatistics = statisticsService.getDeploymentStatistics(ACCOUNT_ID, APP_ID, 30);

    assertThat(deploymentStatistics.getStatsMap()).hasSize(3).containsOnlyKeys(EnvironmentType.values());

    AggregatedDayStats aggregatedProdDayStats = deploymentStatistics.getStatsMap().get(EnvironmentType.PROD);
    AggregatedDayStats aggregatedNonProdDayStats = deploymentStatistics.getStatsMap().get(EnvironmentType.NON_PROD);

    assertThat(aggregatedNonProdDayStats.getFailedCount()).isEqualTo(2);
    assertThat(aggregatedNonProdDayStats.getInstancesCount()).isEqualTo(2);
    assertThat(aggregatedNonProdDayStats.getTotalCount()).isEqualTo(2);
    assertThat(aggregatedNonProdDayStats.getDaysStats().size()).isEqualTo(30);
    assertThat(aggregatedNonProdDayStats.getDaysStats().get(0)).isEqualTo(new DayStat(2, 2, 2, startEpoch));

    assertThat(aggregatedProdDayStats.getFailedCount()).isEqualTo(0);
    assertThat(aggregatedProdDayStats.getInstancesCount()).isEqualTo(2);
    assertThat(aggregatedProdDayStats.getTotalCount()).isEqualTo(2);
    assertThat(aggregatedProdDayStats.getDaysStats().size()).isEqualTo(30);
    assertThat(aggregatedProdDayStats.getDaysStats().get(29)).isEqualTo(new DayStat(2, 0, 2, endEpoch));
  }

  @Test
  public void shouldGetNotificationCount() {
    when(notificationService.list(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(asList(anApprovalNotification().build())).build())
        .thenReturn(aPageResponse()
                        .withResponse(asList(anInformationNotification().build(), anInformationNotification().build()))
                        .build());
    when(activityService.list(any(PageRequest.class)))
        .thenReturn(PageResponse.Builder.aPageResponse().withResponse(asList(anActivity().build())).build());

    NotificationCount notificationCount = statisticsService.getNotificationCount(ACCOUNT_ID, APP_ID, 30);
    assertThat(notificationCount)
        .isEqualTo(aNotificationCount()
                       .withCompletedNotificationsCount(2)
                       .withFailureCount(1)
                       .withPendingNotificationsCount(1)
                       .build());
  }
}
