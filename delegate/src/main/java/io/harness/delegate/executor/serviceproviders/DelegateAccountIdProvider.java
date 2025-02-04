/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.serviceproviders;

import io.harness.delegate.DelegateConfigurationServiceProvider;

import com.google.inject.Singleton;

@Singleton
public class DelegateAccountIdProvider implements DelegateConfigurationServiceProvider {
  @Override
  public String getAccount() {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }
}
