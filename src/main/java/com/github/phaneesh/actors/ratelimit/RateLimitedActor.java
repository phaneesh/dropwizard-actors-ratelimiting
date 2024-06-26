package com.github.phaneesh.actors.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.map.IMap;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.optimization.Optimization;
import io.github.bucket4j.grid.hazelcast.HazelcastProxyManager;
import java.time.Duration;
import java.util.Set;

public abstract class RateLimitedActor<MessageType extends Enum<MessageType>, Message>
    extends Actor<MessageType, Message> {

  private final Bucket bucket;

    private final RateLimitConfiguration.RateLimitType rateLimitType;

  protected RateLimitedActor(
      MessageType type,
      ActorConfig config,
      ConnectionRegistry connectionRegistry,
      ObjectMapper mapper,
      RetryStrategyFactory retryStrategyFactory,
      ExceptionHandlingFactory exceptionHandlingFactory,
      Class<? extends Message> clazz,
      Set<Class<?>> droppedExceptionTypes,
      RateLimitConfiguration rateLimitConfiguration,
      IMap<String, byte[]> bucketMap) {
    super(
        type,
        config,
        connectionRegistry,
        mapper,
        retryStrategyFactory,
        exceptionHandlingFactory,
        clazz,
        droppedExceptionTypes);
    this.rateLimitType = rateLimitConfiguration.getRateLimitType();
    var bandwidth =
        Bandwidth.builder()
            .capacity(rateLimitConfiguration.getRateLimit())
            .refillGreedy(
                rateLimitConfiguration.getRefillRate(),
                Duration.of(
                    rateLimitConfiguration.getRateLimitPeriod(),
                    rateLimitConfiguration.getRateLimitPeriodUnit()))
            .build();
    BucketConfiguration bucketConfiguration =
        BucketConfiguration.builder().addLimit(bandwidth).build();
      HazelcastProxyManager<String> hazelcastProxyManager = new HazelcastProxyManager<>(bucketMap);
    this.bucket =
        hazelcastProxyManager
            .builder()
            .withOptimization(Optimization.NONE_OPTIMIZED)
            .withImplicitConfigurationReplacement(
                1, rateLimitConfiguration.getTokensInheritanceStrategy())
            .build(rateLimitConfiguration.getBucketName(), () -> bucketConfiguration);
  }

  @Override
  protected final boolean handle(Message message, MessageMetadata messageMetadata)
      throws Exception {
    if (rateLimitType == RateLimitConfiguration.RateLimitType.REJECT) {
      if (bucket.tryConsume(1)) {
        return handleWithRateLimit(message, messageMetadata);
      } else {
        return false;
      }
    } else {
      bucket.asBlocking().consume(1);
      return handleWithRateLimit(message, messageMetadata);
    }
  }

  public void reconfigure(RateLimitConfiguration rateLimitConfiguration) {
    var bandwidth =
            Bandwidth.builder()
                    .capacity(rateLimitConfiguration.getRateLimit())
                    .refillGreedy(
                            rateLimitConfiguration.getRefillRate(),
                            Duration.of(
                                    rateLimitConfiguration.getRateLimitPeriod(),
                                    rateLimitConfiguration.getRateLimitPeriodUnit()))
                    .build();
    BucketConfiguration bucketConfiguration =
            BucketConfiguration.builder().addLimit(bandwidth).build();
    bucket.replaceConfiguration(
        bucketConfiguration, rateLimitConfiguration.getTokensInheritanceStrategy());
  }

  protected abstract boolean handleWithRateLimit(Message message, MessageMetadata messageMetadata)
      throws Exception;
}
