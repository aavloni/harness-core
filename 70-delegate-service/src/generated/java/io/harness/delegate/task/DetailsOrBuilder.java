// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: task.proto

package io.harness.delegate.task;

@javax.annotation.Generated(value = "protoc", comments = "annotations:DetailsOrBuilder.java.pb.meta")
public interface DetailsOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.harness.delegate.task.Details)
    com.google.protobuf.MessageOrBuilder {
  /**
   * <code>string type = 1;</code>
   */
  java.lang.String getType();
  /**
   * <code>string type = 1;</code>
   */
  com.google.protobuf.ByteString getTypeBytes();

  /**
   * <code>bytes kryoParameters = 2;</code>
   */
  com.google.protobuf.ByteString getKryoParameters();

  /**
   * <code>.google.protobuf.Duration execution_timeout = 3;</code>
   */
  boolean hasExecutionTimeout();
  /**
   * <code>.google.protobuf.Duration execution_timeout = 3;</code>
   */
  com.google.protobuf.Duration getExecutionTimeout();
  /**
   * <code>.google.protobuf.Duration execution_timeout = 3;</code>
   */
  com.google.protobuf.DurationOrBuilder getExecutionTimeoutOrBuilder();

  /**
   * <code>int64 expression_functor_token = 4;</code>
   */
  long getExpressionFunctorToken();
}
