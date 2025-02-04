/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.io.Serializable;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;

/**
 * Created by srinivas on 4/5/17.
 */
@XmlType(name = "data")
@XmlAccessorType(XmlAccessType.FIELD)
@lombok.Data
@OwnedBy(HarnessTeam.CDC)
public class Data implements Serializable {
  private String type;
  private boolean leaf;
  private String nodeName;
  private String path;

  @XmlElementWrapper(name = "children")
  @XmlElement(name = "indexBrowserTreeNode")
  private List<IndexBrowserTreeNode> children;
}
