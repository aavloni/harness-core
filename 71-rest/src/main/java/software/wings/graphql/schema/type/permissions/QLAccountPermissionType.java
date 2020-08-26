package software.wings.graphql.schema.type.permissions;

import software.wings.graphql.schema.type.QLEnum;

public enum QLAccountPermissionType implements QLEnum {
  CREATE_AND_DELETE_APPLICATION,
  READ_USERS_AND_GROUPS,
  MANAGE_USERS_AND_GROUPS,
  MANAGE_TEMPLATE_LIBRARY,
  ADMINISTER_OTHER_ACCOUNT_FUNCTIONS,
  VIEW_AUDITS,
  MANAGE_TAGS,
  ADMINISTER_CE,
  VIEW_CE,
  /**
   * Manage Cloud Providers
   */
  MANAGE_CLOUD_PROVIDERS,

  /**
   * Manage Connectors
   */
  MANAGE_CONNECTORS,

  /**
   * Manage Application Stacks
   */
  MANAGE_APPLICATION_STACKS,

  /**
   * Manage Delegates
   */
  MANAGE_DELEGATES,

  /**
   * Manage Alert Notification Rules
   */
  MANAGE_ALERT_NOTIFICATION_RULES,

  /**
   * Manage Delegate profiles
   */
  MANAGE_DELEGATE_PROFILES,

  /**
   * Manage Config-as-code
   */
  MANAGE_CONFIG_AS_CODE,

  /**
   * Manage Secrets
   */
  MANAGE_SECRETS,

  /**
   * Manage Secret Managers
   */
  MANAGE_SECRET_MANAGERS,

  /**
   * Manage Authentication Settings
   */
  MANAGE_AUTHENTICATION_SETTINGS,

  /**
   * Manage User, User Groups and API keys
   */
  MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS,

  /**
   *  View User, User Groups and API keys
   */
  VIEW_USER_AND_USER_GROUPS_AND_API_KEYS,

  /**
   * Manage IP Whitelist
   */
  MANAGE_IP_WHITELIST,

  /**
   * Manage Deployment Freezes
   */
  MANAGE_DEPLOYMENT_FREEZES,

  /**
   * Manage Pipeline Governance Standards
   */
  MANAGE_PIPELINE_GOVERNANCE_STANDARDS,

  /**
   * Manage API Keys
   */
  MANAGE_API_KEYS;

  @Override
  public String getStringValue() {
    return this.name();
  }
}
