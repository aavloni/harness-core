package software.wings.helpers.ext.mail;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.jersey.JsonViews;
import software.wings.security.annotations.Encrypted;
import software.wings.security.encryption.Encryptable;
import software.wings.settings.SettingValue;
import software.wings.stencils.DefaultValue;

import java.util.Arrays;
import java.util.List;

/**
 * Created by peeyushaggarwal on 5/20/16.
 */
@JsonTypeName("SMTP")
public class SmtpConfig extends SettingValue implements Encryptable {
  @Attributes(title = "Host", required = true) @NotEmpty private String host;
  @Attributes(title = "Port", required = true) private int port;
  @DefaultValue("wings") @Attributes(title = "From Address") private String fromAddress;
  @DefaultValue("true") @Attributes(title = "SSL") private boolean useSSL;
  @Attributes(title = "Username") private String username;
  @JsonView(JsonViews.Internal.class) @Attributes(title = "Password") @Encrypted private char[] password;
  @SchemaIgnore @NotEmpty private String accountId;
  @Attributes(title = "Encrypted Fields", required = true)
  private List<String> encryptedFields = Arrays.asList("password");

  /**
   * Instantiates a new smtp config.
   */
  public SmtpConfig() {
    super(SettingVariableTypes.SMTP.name());
  }

  /**
   * Gets the list of fields that are encrypted for use in the UI
   * @return List of field names
   */
  public List<String> getEncryptedFields() {
    return encryptedFields;
  }

  /**
   * Gets host.
   *
   * @return the host
   */
  public String getHost() {
    return host;
  }

  /**
   * Sets host.
   *
   * @param host the host
   */
  public void setHost(String host) {
    this.host = host;
  }

  /**
   * Gets port.
   *
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets port.
   *
   * @param port the port
   */
  public void setPort(int port) {
    this.port = port;
  }

  /**
   * Is use ssl boolean.
   *
   * @return the boolean
   */
  public boolean isUseSSL() {
    return useSSL;
  }

  /**
   * Sets use ssl.
   *
   * @param useSSL the use ssl
   */
  public void setUseSSL(boolean useSSL) {
    this.useSSL = useSSL;
  }

  /**
   * Gets username.
   *
   * @return the username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Sets username.
   *
   * @param username the username
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Gets password.
   *
   * @return the password
   */
  //@JsonIgnore
  public char[] getPassword() {
    return password;
  }

  /**
   * Sets password.
   *
   * @param password the password
   */
  //@JsonProperty
  public void setPassword(char[] password) {
    this.password = password;
  }

  @Override
  @SchemaIgnore
  public String getAccountId() {
    return accountId;
  }

  @Override
  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  /**
   * Gets from address.
   *
   * @return the from address
   */
  public String getFromAddress() {
    return fromAddress;
  }

  /**
   * Sets from address.
   *
   * @param fromAddress the from address
   */
  public void setFromAddress(String fromAddress) {
    this.fromAddress = fromAddress;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SmtpConfig config = (SmtpConfig) o;
    return port == config.port && useSSL == config.useSSL && Objects.equal(host, config.host)
        && Objects.equal(fromAddress, config.fromAddress) && Objects.equal(username, config.username)
        && Arrays.equals(password, config.password);
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(host, port, fromAddress, useSSL, username, password);
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("host", host)
        .add("port", port)
        .add("fromAddress", fromAddress)
        .add("useSSL", useSSL)
        .add("username", username)
        .toString();
  }

  /**
   * The Class Builder.
   */
  public static final class Builder {
    private String host;
    private int port;
    private String fromAddress;
    private boolean useSSL;
    private String username;
    private char[] password;
    private String accountId;

    private Builder() {}

    /**
     * A smtp config.
     *
     * @return the builder
     */
    public static Builder aSmtpConfig() {
      return new Builder();
    }

    /**
     * With host.
     *
     * @param host the host
     * @return the builder
     */
    public Builder withHost(String host) {
      this.host = host;
      return this;
    }

    /**
     * With port.
     *
     * @param port the port
     * @return the builder
     */
    public Builder withPort(int port) {
      this.port = port;
      return this;
    }

    /**
     * With from address.
     *
     * @param fromAddress the from address
     * @return the builder
     */
    public Builder withFromAddress(String fromAddress) {
      this.fromAddress = fromAddress;
      return this;
    }

    /**
     * With use ssl.
     *
     * @param useSSL the use ssl
     * @return the builder
     */
    public Builder withUseSSL(boolean useSSL) {
      this.useSSL = useSSL;
      return this;
    }

    /**
     * With username.
     *
     * @param username the username
     * @return the builder
     */
    public Builder withUsername(String username) {
      this.username = username;
      return this;
    }

    /**
     * With password.
     *
     * @param password the password
     * @return the builder
     */
    public Builder withPassword(char[] password) {
      this.password = password;
      return this;
    }

    /**
     * With accountId.
     *
     * @param accountId the accountId
     * @return the builder
     */
    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * But.
     *
     * @return the builder
     */
    public Builder but() {
      return aSmtpConfig()
          .withHost(host)
          .withPort(port)
          .withFromAddress(fromAddress)
          .withUseSSL(useSSL)
          .withUsername(username)
          .withPassword(password)
          .withAccountId(accountId);
    }

    /**
     * Builds the.
     *
     * @return the smtp config
     */
    public SmtpConfig build() {
      SmtpConfig smtpConfig = new SmtpConfig();
      smtpConfig.setHost(host);
      smtpConfig.setPort(port);
      smtpConfig.setFromAddress(fromAddress);
      smtpConfig.setUseSSL(useSSL);
      smtpConfig.setUsername(username);
      smtpConfig.setPassword(password);
      smtpConfig.setAccountId(accountId);
      return smtpConfig;
    }
  }
}
