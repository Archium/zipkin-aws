/*
 * Copyright 2016-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.reporter.awssdk.sqs;

import java.io.IOException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.codec.Encoding;

public final class SQSSender extends AbstractSender {

  public static SQSSender create(String queueUrl) {
    return newBuilder()
        .queueUrl(queueUrl)
        .sqsClient(SqsClient.create())
        .build();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {

    private SqsClient sqsClient;
    private Encoding encoding = Encoding.JSON;
    private String queueUrl;
    private int messageMaxBytes = 256 * 1024; // 256KB SQS limit

    public Builder queueUrl(String queueUrl) {
      if (queueUrl == null) throw new NullPointerException("queueUrl == null");
      this.queueUrl = queueUrl;
      return this;
    }

    public Builder sqsClient(SqsClient sqsClient) {
      if (sqsClient == null) throw new NullPointerException("sqsClient == null");
      this.sqsClient = sqsClient;
      return this;
    }

    public Builder messageMaxBytes(int messageMaxBytes) {
      this.messageMaxBytes = messageMaxBytes;
      return this;
    }

    public Builder encoding(Encoding encoding) {
      this.encoding = encoding;
      return this;
    }

    public SQSSender build() {
      return new SQSSender(this);
    }

    Builder(SQSSender sender) {
      this.sqsClient = sender.sqsClient;
      this.encoding = sender.encoding;
      this.queueUrl = sender.queueUrl;
      this.messageMaxBytes = sender.messageMaxBytes;
    }

    Builder() {}
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  private final SqsClient sqsClient;

  SQSSender(Builder builder) {
    super(builder.encoding, builder.messageMaxBytes, builder.queueUrl);
    this.sqsClient = builder.sqsClient;
  }

  @Override public synchronized void close() {
    if (closeCalled) return;
    sqsClient.close();
    closeCalled = true;
  }

  @Override
  protected Call<Void> call(SendMessageRequest request) {
    return new SQSCall(request);
  }

  @Override
  public final String toString() {
    return "SQSSender{queueUrl= " + queueUrl + "}";
  }

  class SQSCall extends Call.Base<Void> {

    private final SendMessageRequest message;

    SQSCall(SendMessageRequest message) {
      this.message = message;
    }

    @Override
    protected Void doExecute() throws IOException {
      sqsClient.sendMessage(message);
      return null;
    }

    @Override
    protected void doEnqueue(Callback<Void> callback) {
      try {
        sqsClient.sendMessage(message);
        callback.onSuccess(null);
      } catch (RuntimeException e) {
        callback.onError(e);
      }
    }

    @Override
    public Call<Void> clone() {
      return new SQSCall(message);
    }
  }
}
