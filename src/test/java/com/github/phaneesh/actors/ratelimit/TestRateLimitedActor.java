package com.github.phaneesh.actors.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.map.IMap;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TestRateLimitedActor extends RateLimitedActor<ActorMessageType, Map> {

    @Getter
    private AtomicInteger counter;

    @Builder
    protected TestRateLimitedActor(ActorConfig config,
                                   ConnectionRegistry connectionRegistry, ObjectMapper mapper,
                                   RetryStrategyFactory retryStrategyFactory, ExceptionHandlingFactory exceptionHandlingFactory,
                                   Class<? extends Map> clazz, Set<Class<?>> droppedExceptionTypes,
                                   RateLimitConfiguration rateLimitConfiguration, IMap<String, byte[]> bucketMap) {
        super(ActorMessageType.TEST, config, connectionRegistry, mapper, retryStrategyFactory, exceptionHandlingFactory,
                clazz, droppedExceptionTypes, rateLimitConfiguration, bucketMap);
        this.counter = new AtomicInteger(0);
    }

    @Override
    protected boolean handleWithRateLimit(Map map, MessageMetadata messageMetadata)  {
        log.info("Actor time: {}", System.currentTimeMillis() +" | Counter: " + counter.incrementAndGet());
        return true;
    }
}
