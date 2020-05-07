// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: delegate_service.proto

package io.harness.delegate.progress;

/**
 * Protobuf type {@code io.harness.delegate.progress.SubmitTaskRequest}
 */
@javax.annotation.Generated(value = "protoc", comments = "annotations:SubmitTaskRequest.java.pb.meta")
public final class SubmitTaskRequest extends com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:io.harness.delegate.progress.SubmitTaskRequest)
    SubmitTaskRequestOrBuilder {
  private static final long serialVersionUID = 0L;
  // Use SubmitTaskRequest.newBuilder() to construct.
  private SubmitTaskRequest(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private SubmitTaskRequest() {}

  @java.
  lang.Override
  public final com.google.protobuf.UnknownFieldSet getUnknownFields() {
    return this.unknownFields;
  }
  private SubmitTaskRequest(
      com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new java.lang.NullPointerException();
    }
    int mutable_bitField0_ = 0;
    com.google.protobuf.UnknownFieldSet.Builder unknownFields = com.google.protobuf.UnknownFieldSet.newBuilder();
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          case 10: {
            io.harness.delegate.task.SetupAbstractions.Builder subBuilder = null;
            if (setupAbstractions_ != null) {
              subBuilder = setupAbstractions_.toBuilder();
            }
            setupAbstractions_ =
                input.readMessage(io.harness.delegate.task.SetupAbstractions.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(setupAbstractions_);
              setupAbstractions_ = subBuilder.buildPartial();
            }

            break;
          }
          case 18: {
            io.harness.delegate.task.Details.Builder subBuilder = null;
            if (details_ != null) {
              subBuilder = details_.toBuilder();
            }
            details_ = input.readMessage(io.harness.delegate.task.Details.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(details_);
              details_ = subBuilder.buildPartial();
            }

            break;
          }
          default: {
            if (!parseUnknownField(input, unknownFields, extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(this);
    } finally {
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return io.harness.delegate.progress.DelegateServiceOuterClass
        .internal_static_io_harness_delegate_progress_SubmitTaskRequest_descriptor;
  }

  @java.
  lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
    return io.harness.delegate.progress.DelegateServiceOuterClass
        .internal_static_io_harness_delegate_progress_SubmitTaskRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(io.harness.delegate.progress.SubmitTaskRequest.class,
            io.harness.delegate.progress.SubmitTaskRequest.Builder.class);
  }

  public static final int SETUP_ABSTRACTIONS_FIELD_NUMBER = 1;
  private io.harness.delegate.task.SetupAbstractions setupAbstractions_;
  /**
   * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
   */
  public boolean hasSetupAbstractions() {
    return setupAbstractions_ != null;
  }
  /**
   * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
   */
  public io.harness.delegate.task.SetupAbstractions getSetupAbstractions() {
    return setupAbstractions_ == null ? io.harness.delegate.task.SetupAbstractions.getDefaultInstance()
                                      : setupAbstractions_;
  }
  /**
   * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
   */
  public io.harness.delegate.task.SetupAbstractionsOrBuilder getSetupAbstractionsOrBuilder() {
    return getSetupAbstractions();
  }

  public static final int DETAILS_FIELD_NUMBER = 2;
  private io.harness.delegate.task.Details details_;
  /**
   * <code>.io.harness.delegate.task.Details details = 2;</code>
   */
  public boolean hasDetails() {
    return details_ != null;
  }
  /**
   * <code>.io.harness.delegate.task.Details details = 2;</code>
   */
  public io.harness.delegate.task.Details getDetails() {
    return details_ == null ? io.harness.delegate.task.Details.getDefaultInstance() : details_;
  }
  /**
   * <code>.io.harness.delegate.task.Details details = 2;</code>
   */
  public io.harness.delegate.task.DetailsOrBuilder getDetailsOrBuilder() {
    return getDetails();
  }

  private byte memoizedIsInitialized = -1;
  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1)
      return true;
    if (isInitialized == 0)
      return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
    if (setupAbstractions_ != null) {
      output.writeMessage(1, getSetupAbstractions());
    }
    if (details_ != null) {
      output.writeMessage(2, getDetails());
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1)
      return size;

    size = 0;
    if (setupAbstractions_ != null) {
      size += com.google.protobuf.CodedOutputStream.computeMessageSize(1, getSetupAbstractions());
    }
    if (details_ != null) {
      size += com.google.protobuf.CodedOutputStream.computeMessageSize(2, getDetails());
    }
    size += unknownFields.getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof io.harness.delegate.progress.SubmitTaskRequest)) {
      return super.equals(obj);
    }
    io.harness.delegate.progress.SubmitTaskRequest other = (io.harness.delegate.progress.SubmitTaskRequest) obj;

    if (hasSetupAbstractions() != other.hasSetupAbstractions())
      return false;
    if (hasSetupAbstractions()) {
      if (!getSetupAbstractions().equals(other.getSetupAbstractions()))
        return false;
    }
    if (hasDetails() != other.hasDetails())
      return false;
    if (hasDetails()) {
      if (!getDetails().equals(other.getDetails()))
        return false;
    }
    if (!unknownFields.equals(other.unknownFields))
      return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    if (hasSetupAbstractions()) {
      hash = (37 * hash) + SETUP_ABSTRACTIONS_FIELD_NUMBER;
      hash = (53 * hash) + getSetupAbstractions().hashCode();
    }
    if (hasDetails()) {
      hash = (37 * hash) + DETAILS_FIELD_NUMBER;
      hash = (53 * hash) + getDetails().hashCode();
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(
      java.nio.ByteBuffer data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(
      com.google.protobuf.ByteString data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(
      byte[] data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(PARSER, input);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseDelimitedFrom(java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
  }
  public static io.harness.delegate.progress.SubmitTaskRequest parseFrom(com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() {
    return newBuilder();
  }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(io.harness.delegate.progress.SubmitTaskRequest prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code io.harness.delegate.progress.SubmitTaskRequest}
   */
  public static final class Builder extends com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:io.harness.delegate.progress.SubmitTaskRequest)
      io.harness.delegate.progress.SubmitTaskRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return io.harness.delegate.progress.DelegateServiceOuterClass
          .internal_static_io_harness_delegate_progress_SubmitTaskRequest_descriptor;
    }

    @java.
    lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
      return io.harness.delegate.progress.DelegateServiceOuterClass
          .internal_static_io_harness_delegate_progress_SubmitTaskRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(io.harness.delegate.progress.SubmitTaskRequest.class,
              io.harness.delegate.progress.SubmitTaskRequest.Builder.class);
    }

    // Construct using io.harness.delegate.progress.SubmitTaskRequest.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders) {
      }
    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      if (setupAbstractionsBuilder_ == null) {
        setupAbstractions_ = null;
      } else {
        setupAbstractions_ = null;
        setupAbstractionsBuilder_ = null;
      }
      if (detailsBuilder_ == null) {
        details_ = null;
      } else {
        details_ = null;
        detailsBuilder_ = null;
      }
      return this;
    }

    @java.
    lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return io.harness.delegate.progress.DelegateServiceOuterClass
          .internal_static_io_harness_delegate_progress_SubmitTaskRequest_descriptor;
    }

    @java.
    lang.Override
    public io.harness.delegate.progress.SubmitTaskRequest getDefaultInstanceForType() {
      return io.harness.delegate.progress.SubmitTaskRequest.getDefaultInstance();
    }

    @java.
    lang.Override
    public io.harness.delegate.progress.SubmitTaskRequest build() {
      io.harness.delegate.progress.SubmitTaskRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.
    lang.Override
    public io.harness.delegate.progress.SubmitTaskRequest buildPartial() {
      io.harness.delegate.progress.SubmitTaskRequest result = new io.harness.delegate.progress.SubmitTaskRequest(this);
      if (setupAbstractionsBuilder_ == null) {
        result.setupAbstractions_ = setupAbstractions_;
      } else {
        result.setupAbstractions_ = setupAbstractionsBuilder_.build();
      }
      if (detailsBuilder_ == null) {
        result.details_ = details_;
      } else {
        result.details_ = detailsBuilder_.build();
      }
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder clone() {
      return super.clone();
    }
    @java.lang.Override
    public Builder setField(com.google.protobuf.Descriptors.FieldDescriptor field, java.lang.Object value) {
      return super.setField(field, value);
    }
    @java.lang.Override
    public Builder clearField(com.google.protobuf.Descriptors.FieldDescriptor field) {
      return super.clearField(field);
    }
    @java.lang.Override
    public Builder clearOneof(com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return super.clearOneof(oneof);
    }
    @java.lang.Override
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field, int index, java.lang.Object value) {
      return super.setRepeatedField(field, index, value);
    }
    @java.lang.Override
    public Builder addRepeatedField(com.google.protobuf.Descriptors.FieldDescriptor field, java.lang.Object value) {
      return super.addRepeatedField(field, value);
    }
    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof io.harness.delegate.progress.SubmitTaskRequest) {
        return mergeFrom((io.harness.delegate.progress.SubmitTaskRequest) other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(io.harness.delegate.progress.SubmitTaskRequest other) {
      if (other == io.harness.delegate.progress.SubmitTaskRequest.getDefaultInstance())
        return this;
      if (other.hasSetupAbstractions()) {
        mergeSetupAbstractions(other.getSetupAbstractions());
      }
      if (other.hasDetails()) {
        mergeDetails(other.getDetails());
      }
      this.mergeUnknownFields(other.unknownFields);
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
      io.harness.delegate.progress.SubmitTaskRequest parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (io.harness.delegate.progress.SubmitTaskRequest) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private io.harness.delegate.task.SetupAbstractions setupAbstractions_;
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.task.SetupAbstractions,
        io.harness.delegate.task.SetupAbstractions.Builder, io.harness.delegate.task.SetupAbstractionsOrBuilder>
        setupAbstractionsBuilder_;
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    public boolean hasSetupAbstractions() {
      return setupAbstractionsBuilder_ != null || setupAbstractions_ != null;
    }
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    public io.harness.delegate.task.SetupAbstractions getSetupAbstractions() {
      if (setupAbstractionsBuilder_ == null) {
        return setupAbstractions_ == null ? io.harness.delegate.task.SetupAbstractions.getDefaultInstance()
                                          : setupAbstractions_;
      } else {
        return setupAbstractionsBuilder_.getMessage();
      }
    }
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    public Builder setSetupAbstractions(io.harness.delegate.task.SetupAbstractions value) {
      if (setupAbstractionsBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        setupAbstractions_ = value;
        onChanged();
      } else {
        setupAbstractionsBuilder_.setMessage(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    public Builder setSetupAbstractions(io.harness.delegate.task.SetupAbstractions.Builder builderForValue) {
      if (setupAbstractionsBuilder_ == null) {
        setupAbstractions_ = builderForValue.build();
        onChanged();
      } else {
        setupAbstractionsBuilder_.setMessage(builderForValue.build());
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    public Builder mergeSetupAbstractions(io.harness.delegate.task.SetupAbstractions value) {
      if (setupAbstractionsBuilder_ == null) {
        if (setupAbstractions_ != null) {
          setupAbstractions_ =
              io.harness.delegate.task.SetupAbstractions.newBuilder(setupAbstractions_).mergeFrom(value).buildPartial();
        } else {
          setupAbstractions_ = value;
        }
        onChanged();
      } else {
        setupAbstractionsBuilder_.mergeFrom(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    public Builder clearSetupAbstractions() {
      if (setupAbstractionsBuilder_ == null) {
        setupAbstractions_ = null;
        onChanged();
      } else {
        setupAbstractions_ = null;
        setupAbstractionsBuilder_ = null;
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    public io.harness.delegate.task.SetupAbstractions.Builder getSetupAbstractionsBuilder() {
      onChanged();
      return getSetupAbstractionsFieldBuilder().getBuilder();
    }
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    public io.harness.delegate.task.SetupAbstractionsOrBuilder getSetupAbstractionsOrBuilder() {
      if (setupAbstractionsBuilder_ != null) {
        return setupAbstractionsBuilder_.getMessageOrBuilder();
      } else {
        return setupAbstractions_ == null ? io.harness.delegate.task.SetupAbstractions.getDefaultInstance()
                                          : setupAbstractions_;
      }
    }
    /**
     * <code>.io.harness.delegate.task.SetupAbstractions setup_abstractions = 1;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.task.SetupAbstractions,
        io.harness.delegate.task.SetupAbstractions.Builder, io.harness.delegate.task.SetupAbstractionsOrBuilder>
    getSetupAbstractionsFieldBuilder() {
      if (setupAbstractionsBuilder_ == null) {
        setupAbstractionsBuilder_ =
            new com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.task.SetupAbstractions,
                io.harness.delegate.task.SetupAbstractions.Builder,
                io.harness.delegate.task.SetupAbstractionsOrBuilder>(
                getSetupAbstractions(), getParentForChildren(), isClean());
        setupAbstractions_ = null;
      }
      return setupAbstractionsBuilder_;
    }

    private io.harness.delegate.task.Details details_;
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.task.Details,
        io.harness.delegate.task.Details.Builder, io.harness.delegate.task.DetailsOrBuilder> detailsBuilder_;
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    public boolean hasDetails() {
      return detailsBuilder_ != null || details_ != null;
    }
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    public io.harness.delegate.task.Details getDetails() {
      if (detailsBuilder_ == null) {
        return details_ == null ? io.harness.delegate.task.Details.getDefaultInstance() : details_;
      } else {
        return detailsBuilder_.getMessage();
      }
    }
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    public Builder setDetails(io.harness.delegate.task.Details value) {
      if (detailsBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        details_ = value;
        onChanged();
      } else {
        detailsBuilder_.setMessage(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    public Builder setDetails(io.harness.delegate.task.Details.Builder builderForValue) {
      if (detailsBuilder_ == null) {
        details_ = builderForValue.build();
        onChanged();
      } else {
        detailsBuilder_.setMessage(builderForValue.build());
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    public Builder mergeDetails(io.harness.delegate.task.Details value) {
      if (detailsBuilder_ == null) {
        if (details_ != null) {
          details_ = io.harness.delegate.task.Details.newBuilder(details_).mergeFrom(value).buildPartial();
        } else {
          details_ = value;
        }
        onChanged();
      } else {
        detailsBuilder_.mergeFrom(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    public Builder clearDetails() {
      if (detailsBuilder_ == null) {
        details_ = null;
        onChanged();
      } else {
        details_ = null;
        detailsBuilder_ = null;
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    public io.harness.delegate.task.Details.Builder getDetailsBuilder() {
      onChanged();
      return getDetailsFieldBuilder().getBuilder();
    }
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    public io.harness.delegate.task.DetailsOrBuilder getDetailsOrBuilder() {
      if (detailsBuilder_ != null) {
        return detailsBuilder_.getMessageOrBuilder();
      } else {
        return details_ == null ? io.harness.delegate.task.Details.getDefaultInstance() : details_;
      }
    }
    /**
     * <code>.io.harness.delegate.task.Details details = 2;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.task.Details,
        io.harness.delegate.task.Details.Builder, io.harness.delegate.task.DetailsOrBuilder>
    getDetailsFieldBuilder() {
      if (detailsBuilder_ == null) {
        detailsBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.task.Details,
            io.harness.delegate.task.Details.Builder, io.harness.delegate.task.DetailsOrBuilder>(
            getDetails(), getParentForChildren(), isClean());
        details_ = null;
      }
      return detailsBuilder_;
    }
    @java.lang.Override
    public final Builder setUnknownFields(final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFields(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }

    // @@protoc_insertion_point(builder_scope:io.harness.delegate.progress.SubmitTaskRequest)
  }

  // @@protoc_insertion_point(class_scope:io.harness.delegate.progress.SubmitTaskRequest)
  private static final io.harness.delegate.progress.SubmitTaskRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new io.harness.delegate.progress.SubmitTaskRequest();
  }

  public static io.harness.delegate.progress.SubmitTaskRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<SubmitTaskRequest> PARSER =
      new com.google.protobuf.AbstractParser<SubmitTaskRequest>() {
        @java.lang.Override
        public SubmitTaskRequest parsePartialFrom(
            com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
          return new SubmitTaskRequest(input, extensionRegistry);
        }
      };

  public static com.google.protobuf.Parser<SubmitTaskRequest> parser() {
    return PARSER;
  }

  @java.
  lang.Override
  public com.google.protobuf.Parser<SubmitTaskRequest> getParserForType() {
    return PARSER;
  }

  @java.
  lang.Override
  public io.harness.delegate.progress.SubmitTaskRequest getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }
}
