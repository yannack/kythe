/*
 * Copyright 2016 The Kythe Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.kythe.platform.shared;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.devtools.kythe.analyzers.base.EdgeKind;
import com.google.devtools.kythe.proto.Metadata.GeneratedCodeInfo;
import com.google.devtools.kythe.proto.Metadata.MappingRule;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Loader that loads inline metadata from Java files generated by codegen that specify inline
 * metadata.
 *
 * <p>The metadata is added as a comment on the last line of the Java file, as a base64 encoded
 * string, which maps the Java symbols to the source language that the Java file was generated from.
 */
public final class KytheInlineMetadataLoader implements MetadataLoader {
  private static final String ANNOTATION_PREFIX_STRING = "kythe-inline-metadata:";
  private static final byte[] ANNOTATION_PREFIX = ANNOTATION_PREFIX_STRING.getBytes(UTF_8);
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public KytheInlineMetadataLoader() {}

  @Override
  public Metadata parseFile(String fileName, byte[] data) {
    if (!isCodegenFile(data)) {
      return null;
    }
    GeneratedCodeInfo javaMetadata;
    try {
      javaMetadata = extractMetadata(data);
    } catch (InvalidProtocolBufferException e) {
      logger.atWarning().withCause(e).log(
          "Error parsing GeneratedCodeInfo from file: %s", fileName);
      return null;
    }
    if (javaMetadata == null) {
      logger.atWarning().log("Error parsing GeneratedCodeInfo from file: %s", fileName);
      return null;
    }
    return constructMetadata(javaMetadata);
  }

  private static boolean isCodegenFile(byte[] data) {
    if (data == null || data.length < ANNOTATION_PREFIX.length) {
      return false;
    }
    for (int i = 0; i < ANNOTATION_PREFIX.length; i++) {
      if (data[i] != ANNOTATION_PREFIX[i]) {
        return false;
      }
    }
    return true;
  }

  private static GeneratedCodeInfo extractMetadata(byte[] data)
      throws InvalidProtocolBufferException {
    String metadata = new String(data, UTF_8).substring(ANNOTATION_PREFIX_STRING.length());
    byte[] protoData = BaseEncoding.base64().decode(metadata);
    return GeneratedCodeInfo.parseFrom(protoData);
  }

  private static Metadata constructMetadata(GeneratedCodeInfo javaMetadata) {
    Metadata metadata = new Metadata();
    for (MappingRule mapping : javaMetadata.getMetaList()) {
      Metadata.Rule rule = new Metadata.Rule();
      rule.begin = mapping.getBegin();
      rule.end = mapping.getEnd();
      rule.vname = mapping.getVname();
      rule.edgeOut = EdgeKind.GENERATES;
      rule.reverseEdge = true;
      metadata.addRule(rule);
    }
    return metadata;
  }
}
