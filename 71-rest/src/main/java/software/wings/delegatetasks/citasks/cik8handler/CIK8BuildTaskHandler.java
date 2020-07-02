package software.wings.delegatetasks.citasks.cik8handler;

/**
 * Delegate task handler to setup CI build environment on a K8 cluster including creation of pod as well as image and
 * git secrets.
 */

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.AutoLogContext;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.KubernetesConfig;
import software.wings.beans.ci.CIBuildSetupTaskParams;
import software.wings.beans.ci.CIK8BuildTaskParams;
import software.wings.beans.ci.pod.CIK8ContainerParams;
import software.wings.beans.ci.pod.CIK8PodParams;
import software.wings.beans.ci.pod.ImageDetailsWithConnector;
import software.wings.beans.ci.pod.PodParams;
import software.wings.beans.ci.pod.SecretKeyParams;
import software.wings.beans.container.ImageDetails;
import software.wings.delegatetasks.citasks.CIBuildTaskHandler;
import software.wings.delegatetasks.citasks.cik8handler.pod.CIK8PodSpecBuilder;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.impl.KubernetesHelperService;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;

@Slf4j
@Singleton
public class CIK8BuildTaskHandler implements CIBuildTaskHandler {
  @Inject private KubernetesHelperService kubernetesHelperService;
  @Inject private CIK8CtlHandler kubeCtlHandler;
  @Inject private CIK8PodSpecBuilder podSpecBuilder;
  @NotNull private CIBuildTaskHandler.Type type = CIBuildTaskHandler.Type.GCP_K8;

  private static final String imageIdFormat = "%s-%s";

  @Override
  public CIBuildTaskHandler.Type getType() {
    return type;
  }

  public K8sTaskExecutionResponse executeTaskInternal(CIBuildSetupTaskParams ciBuildSetupTaskParams) {
    CIK8BuildTaskParams cik8BuildTaskParams = (CIK8BuildTaskParams) ciBuildSetupTaskParams;
    GitFetchFilesConfig gitFetchFilesConfig = cik8BuildTaskParams.getGitFetchFilesConfig();
    KubernetesConfig kubernetesConfig = cik8BuildTaskParams.getKubernetesConfig();
    PodParams podParams = cik8BuildTaskParams.getCik8PodParams();
    String namespace = podParams.getNamespace();
    String podName = podParams.getName();

    K8sTaskExecutionResponse result;
    try (AutoLogContext ignore1 = new K8LogContext(podParams.getName(), null, OVERRIDE_ERROR)) {
      try {
        KubernetesClient kubernetesClient = createKubernetesClient(cik8BuildTaskParams);
        createGitSecret(kubernetesClient, kubernetesConfig, gitFetchFilesConfig);
        createImageSecrets(kubernetesClient, namespace, (CIK8PodParams<CIK8ContainerParams>) podParams);
        createEnvVariablesSecrets(kubernetesClient, namespace, (CIK8PodParams<CIK8ContainerParams>) podParams);

        Pod pod = podSpecBuilder.createSpec(podParams).build();
        logger.info("Creating pod with spec: {}", pod);
        kubeCtlHandler.createPod(kubernetesClient, pod, namespace);
        boolean isPodRunning = kubeCtlHandler.waitUntilPodIsReady(kubernetesClient, podName, namespace);
        if (isPodRunning) {
          result = K8sTaskExecutionResponse.builder()
                       .commandExecutionStatus(CommandExecutionResult.CommandExecutionStatus.SUCCESS)
                       .build();
        } else {
          result = K8sTaskExecutionResponse.builder()
                       .commandExecutionStatus(CommandExecutionResult.CommandExecutionStatus.FAILURE)
                       .build();
        }
      } catch (Exception ex) {
        logger.error("Exception in processing CI K8 build setup task: {}", ciBuildSetupTaskParams, ex);
        result = K8sTaskExecutionResponse.builder()
                     .commandExecutionStatus(CommandExecutionResult.CommandExecutionStatus.FAILURE)
                     .errorMessage(ex.getMessage())
                     .build();
      }
    }
    return result;
  }

  private KubernetesClient createKubernetesClient(CIK8BuildTaskParams cik8BuildTaskParams) {
    return kubernetesHelperService.getKubernetesClient(
        cik8BuildTaskParams.getKubernetesConfig(), cik8BuildTaskParams.getEncryptionDetails());
  }

  private void createGitSecret(
      KubernetesClient kubernetesClient, KubernetesConfig kubernetesConfig, GitFetchFilesConfig gitFetchFilesConfig) {
    try {
      kubeCtlHandler.createGitSecret(kubernetesClient, kubernetesConfig.getNamespace(),
          gitFetchFilesConfig.getGitConfig(), gitFetchFilesConfig.getEncryptedDataDetails());
    } catch (UnsupportedEncodingException e) {
      String errMsg = format("Unknown format for GIT password %s", e.getMessage());
      logger.error(errMsg);
      throw new InvalidRequestException(errMsg, e, WingsException.USER);
    }
  }

  private void createImageSecrets(
      KubernetesClient kubernetesClient, String namespace, CIK8PodParams<CIK8ContainerParams> podParams) {
    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    Optional.ofNullable(podParams.getContainerParamsList()).ifPresent(containerParamsList::addAll);
    Optional.ofNullable(podParams.getInitContainerParamsList()).ifPresent(containerParamsList::addAll);

    Map<String, ImageDetailsWithConnector> imageDetailsById = new HashMap<>();
    for (CIK8ContainerParams containerParams : containerParamsList) {
      ImageDetails imageDetails = containerParams.getImageDetailsWithConnector().getImageDetails();
      if (isNotBlank(imageDetails.getRegistryUrl())) {
        imageDetailsById.put(format(imageIdFormat, imageDetails.getName(), imageDetails.getRegistryUrl()),
            containerParams.getImageDetailsWithConnector());
      }
    }
    imageDetailsById.forEach(
        (imageId, imageDetails) -> kubeCtlHandler.createRegistrySecret(kubernetesClient, namespace, imageDetails));
  }

  private void createEnvVariablesSecrets(
      KubernetesClient kubernetesClient, String namespace, CIK8PodParams<CIK8ContainerParams> podParams) {
    List<CIK8ContainerParams> containerParamsList = podParams.getContainerParamsList();
    for (CIK8ContainerParams containerParams : containerParamsList) {
      Map<String, EncryptedDataDetail> encryptedSecrets = containerParams.getEncryptedSecrets();
      Map<String, SecretKeyParams> secretEnvVars = new HashMap<>();

      if (isNotEmpty(encryptedSecrets)) {
        Secret secret = kubeCtlHandler.createCustomVarSecret(kubernetesClient, namespace,
            containerParams.getEncryptedSecrets(), podParams.getName(), containerParams.getName());

        for (Map.Entry<String, EncryptedDataDetail> encryptedVariable : encryptedSecrets.entrySet()) {
          secretEnvVars.put(encryptedVariable.getKey(),
              SecretKeyParams.builder()
                  .key(SecretSpecBuilder.SECRET_KEY + encryptedVariable.getKey())
                  .secretName(secret.getMetadata().getName())
                  .build());
        }
      }

      containerParams.setSecretEnvVars(secretEnvVars);
    }
  }
}