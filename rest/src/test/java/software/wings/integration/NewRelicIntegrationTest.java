package software.wings.integration;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import com.google.inject.Inject;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.query.Query;
import software.wings.beans.RestResponse;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.service.impl.analysis.LogDataRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.newrelic.NewRelicService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;

/**
 * Created by rsingh on 9/7/17.
 */
public class NewRelicIntegrationTest extends BaseIntegrationTest {
  private Set<String> hosts = new HashSet<>();
  @Inject private NewRelicService newRelicService;

  @Before
  public void setUp() throws Exception {
    loginAdminUser();
    deleteAllDocuments(Arrays.asList(NewRelicMetricDataRecord.class));
    hosts.clear();
    hosts.add("ip-172-31-2-144");
    hosts.add("ip-172-31-4-253");
    hosts.add("ip-172-31-12-51");
  }

  @Test
  public void testMetricSave() throws Exception {
    final int numOfMinutes = 4;
    final int numOfBatches = 5;
    final int numOfMetricsPerBatch = 100;
    final String workflowId = UUID.randomUUID().toString();
    final String workflowExecutionId = UUID.randomUUID().toString();
    final String serviceId = UUID.randomUUID().toString();
    final String stateExecutionId = UUID.randomUUID().toString();
    final String applicationId = UUID.randomUUID().toString();

    Random r = new Random();

    for (int batchNum = 0; batchNum < numOfBatches; batchNum++) {
      List<NewRelicMetricDataRecord> metricDataRecords = new ArrayList<>();

      for (int metricNum = 0; metricNum < numOfMetricsPerBatch; metricNum++) {
        String metricName = "metric-" + batchNum * numOfMetricsPerBatch + metricNum;
        for (String host : hosts) {
          for (int collectionMin = 0; collectionMin < numOfMinutes; collectionMin++) {
            NewRelicMetricDataRecord record = new NewRelicMetricDataRecord();
            record.setName(metricName);
            record.setHost(host);
            record.setWorkflowId(workflowId);
            record.setWorkflowExecutionId(workflowExecutionId);
            record.setServiceId(serviceId);
            record.setStateExecutionId(stateExecutionId);
            record.setTimeStamp(collectionMin);
            record.setDataCollectionMinute(collectionMin);

            record.setThroughput(r.nextDouble());
            record.setAverageResponseTime(r.nextDouble());
            record.setError(r.nextDouble());
            record.setApdexScore(r.nextDouble());

            metricDataRecords.add(record);

            // add more records for duplicate records for the same time
            if (collectionMin > 0) {
              record = new NewRelicMetricDataRecord();
              record.setName(metricName);
              record.setHost(host);
              record.setWorkflowId(workflowId);
              record.setWorkflowExecutionId(workflowExecutionId);
              record.setServiceId(serviceId);
              record.setStateExecutionId(stateExecutionId);
              // duplicate for previous minute
              record.setTimeStamp(collectionMin - 1);
              record.setDataCollectionMinute(collectionMin);

              record.setThroughput(r.nextDouble());
              record.setAverageResponseTime(r.nextDouble());
              record.setError(r.nextDouble());
              record.setApdexScore(r.nextDouble());

              metricDataRecords.add(record);
            }
          }
        }
      }

      WebTarget target =
          client.target(API_BASE + "/newrelic/save-metrics?accountId=" + accountId + "&applicationId=" + applicationId);
      RestResponse<Boolean> restResponse = getDelegateRequestBuilderWithAuthHeader(target).post(
          Entity.entity(metricDataRecords, APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
      Assert.assertTrue(restResponse.getResource());

      Query<NewRelicMetricDataRecord> query = wingsPersistence.createQuery(NewRelicMetricDataRecord.class);
      Assert.assertEquals((batchNum + 1) * numOfMetricsPerBatch * hosts.size() * numOfMinutes, query.asList().size());
    }
  }
}
