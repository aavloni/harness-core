package migrations.all;

import com.google.inject.Inject;

import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import io.harness.persistence.HIterator;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;
import software.wings.api.DeploymentType;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.dl.WingsPersistence;

@Slf4j
public class PcfServiceDeploymentMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    logger.info("Retrieving PCF Services");
    final DBCollection collection = wingsPersistence.getCollection(Service.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    int i = 1;

    try (HIterator<Service> services = new HIterator<>(
             wingsPersistence.createQuery(Service.class).filter(ServiceKeys.artifactType, "PCF").fetch())) {
      while (services.hasNext()) {
        Service service = services.next();
        if (service.getDeploymentType() != null) {
          continue;
        }

        if (i % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          logger.info("Pcf Service: {} updated", i);
        }
        ++i;

        bulkWriteOperation
            .find(wingsPersistence.createQuery(Service.class)
                      .filter(ServiceKeys.uuid, service.getUuid())
                      .getQueryObject())
            .updateOne(
                new BasicDBObject("$set", new BasicDBObject(ServiceKeys.deploymentType, DeploymentType.PCF.name())));
      }

      if (i % 1000 != 1) {
        bulkWriteOperation.execute();
      }

    } catch (Exception e) {
      logger.warn("Something failed in PcfServiceDeploymentType Migration", e);
    }
  }
}
