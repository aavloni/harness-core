/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.encodeBase64ToByteArray;
import static io.harness.security.SimpleEncryption.CHARSET;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.DecryptedRecord;
import software.wings.service.intfc.DelegateManagerEncryptionDecryptionHarnessSMService;

import com.google.inject.Inject;
import java.nio.ByteBuffer;

@OwnedBy(CDP)
public class DelegateManagerEncryptionDecryptionHarnessSMServiceImpl
    implements DelegateManagerEncryptionDecryptionHarnessSMService {
  @Inject private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Inject private SecretManagerConfigService secretManagerConfigService;

  @Override
  public EncryptedRecordData encryptData(String accountId, byte[] content) {
    String value = new String(CHARSET.decode(ByteBuffer.wrap(encodeBase64ToByteArray(content))).array());
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getGlobalSecretManager(accountId);
    return (EncryptedRecordData) encryptKmsSecret(value, secretManagerConfig);
  }

  private EncryptedRecord encryptKmsSecret(String value, EncryptionConfig encryptionConfig) {
    KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(encryptionConfig);
    return kmsEncryptor.encryptSecret(encryptionConfig.getAccountId(), value, encryptionConfig);
  }

  @Override
  public DecryptedRecord decryptData(String accountId, EncryptedRecordData encryptedRecord) {
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getGlobalSecretManager(accountId);
    return DecryptedRecord.builder().decryptedValue(decryptSecretValue(encryptedRecord, secretManagerConfig)).build();
  }

  private char[] decryptSecretValue(EncryptedRecordData encryptedRecord, EncryptionConfig config) {
    KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(config);
    return kmsEncryptor.fetchSecretValue(config.getAccountId(), encryptedRecord, config);
  }
}
