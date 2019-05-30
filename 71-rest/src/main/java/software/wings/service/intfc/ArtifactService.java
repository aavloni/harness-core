package software.wings.service.intfc;

import static software.wings.beans.artifact.Artifact.Status;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.query.Query;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.Artifact.ContentStatus;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.service.intfc.ownership.OwnedByArtifactStream;

import java.io.File;
import java.util.Collection;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * The Interface ArtifactService.
 */
public interface ArtifactService extends OwnedByArtifactStream {
  /**
   * List.
   *
   * @param pageRequest  the page request
   * @return the page response
   */
  PageResponse<Artifact> listSortByBuildNo(PageRequest<Artifact> pageRequest);

  /***
   * List artifact sort by build nos
   * @param appId
   * @param serviceId
   * @param pageRequest
   * @return
   */
  PageResponse<Artifact> listSortByBuildNo(
      @NotEmpty String appId, String serviceId, @NotNull PageRequest<Artifact> pageRequest);

  /***
   * List artifact sort by build nos
   * @param serviceId
   * @param pageRequest
   * @return
   */
  PageResponse<Artifact> listSortByBuildNo(String serviceId, @NotNull PageRequest<Artifact> pageRequest);

  /**
   * Creates the artifact and validates artifact type
   *
   * @param artifact the artifact
   * @return the artifact
   */
  Artifact create(@Valid Artifact artifact);

  /**
   * Update.
   *
   * @param artifact the artifact
   * @return the artifact
   */
  Artifact update(@Valid Artifact artifact);

  /**
   * Update status.
   *
   * @param artifactId the artifact id
   * @param accountId  the account id
   * @param status     the status
   */
  void updateStatus(String artifactId, String accountId, Status status);

  /**
   * Update status.
   *
   * @param artifactId the artifact id
   * @param accountId  the account id
   * @param status     the status
   */
  void updateStatus(String artifactId, String accountId, Status status, String errorMessage);

  /***
   * Update status and content status
   * @param artifactId
   * @param accountId
   * @param status
   * @param contentStatus
   */
  void updateStatus(String artifactId, String accountId, Status status, ContentStatus contentStatus);

  /***
   * Update status
   * @param artifactId
   * @param accountId
   * @param status
   * @param contentStatus
   * @param errorMessage
   */
  void updateStatus(
      String artifactId, String accountId, Status status, ContentStatus contentStatus, String errorMessage);

  /**
   * Update artifact source name
   * @param artifactStream
   */
  void updateArtifactSourceName(ArtifactStream artifactStream);

  /**
   * Adds the artifact file.
   *
   * @param artifactId    the artifact id
   * @param accountId     the account id
   * @param artifactFiles the artifact files
   */
  void addArtifactFile(String artifactId, String accountId, List<ArtifactFile> artifactFiles);

  /**
   * Download.
   *
   * @param accountId  the account id
   * @param artifactId the artifact id
   * @return the file
   */
  File download(String accountId, String artifactId);

  /**
   * Gets artifact.
   *
   * @param artifactId the artifact id
   * @return the artifact
   */
  Artifact get(String artifactId);

  /**
   * Gets artifact.
   *
   * @param accountId  the account id
   * @param artifactId the artifact id
   * @return the artifact
   */
  Artifact get(String accountId, String artifactId);

  /**
   * Get artifact.
   *
   * @param artifactId   the artifact id
   * @param appId        the app id
   * @return the artifact
   */
  Artifact getWithServices(String artifactId, String appId);

  /**
   * Soft delete.
   *
   * @param appId      the app id
   * @param artifactId the artifact id
   * @return the artifact
   */
  boolean delete(String appId, String artifactId);

  /**
   * Soft delete a list of artifacts.
   *
   * @param artifacts the artifacts
   */
  void deleteArtifacts(List<Artifact> artifacts);

  Artifact fetchLatestArtifactForArtifactStream(ArtifactStream artifactStream);

  Artifact fetchLastCollectedApprovedArtifactForArtifactStream(ArtifactStream artifactStream);

  Artifact fetchLastCollectedArtifact(ArtifactStream artifactStream);

  Artifact getArtifactByBuildNumber(ArtifactStream artifactStream, String buildNumber, boolean regex);

  /**
   * Starts Artifact collection and returns
   * @param accountId
   * @param artifactId
   */
  Artifact startArtifactCollection(String accountId, String artifactId);

  /**
   * Gets content status if artifact does not have content status
   * @param artifact
   * @return
   */
  ContentStatus getArtifactContentStatus(Artifact artifact);

  /**
   * Delete by artifact stream.
   *
   * @param retentionSize the size of the artifacts to be retained
   */
  void deleteArtifacts(int retentionSize);

  Query<Artifact> prepareArtifactWithMetadataQuery(ArtifactStream artifactStream);

  void deleteWhenArtifactSourceNameChanged(ArtifactStream artifactStream);

  List<Artifact> listByIds(String accountId, Collection<String> artifactIds);

  List<Artifact> listByAccountId(String accountId);

  List<Artifact> listByAppId(String appId);
}
