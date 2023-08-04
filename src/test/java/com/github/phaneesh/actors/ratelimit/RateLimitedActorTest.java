package com.github.phaneesh.actors.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.testcontainers.rabbitmq.config.RabbitMQContainerConfiguration;
import io.appform.testcontainers.rabbitmq.container.RabbitMQContainer;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class RateLimitedActorTest {

    public static final DropwizardAppExtension<RateLimitedActorTestAppConfiguration> app =
            new DropwizardAppExtension<>(RateLimitedActorTestApplication.class);
    private static final int RABBITMQ_MANAGEMENT_PORT = 15672;
    private static final String RABBITMQ_DOCKER_IMAGE = "rabbitmq:3.8.34-management";
    private static final String RABBITMQ_USERNAME = "admin";
    private static final String RABBITMQ_PASSWORD = "admin";
    private static ConnectionRegistry connectionRegistry;

    private static RMQConnection rmqConnection;

    private static RabbitMQContainer rabbitMQ;

    private static HazelcastInstance hazelcastInstance;

    private static IMap<String, byte[]> testBucket;

    private static ObjectMapper objectMapper = new ObjectMapper();

    private static String queueName = "TEST";

    @BeforeAll
    @SneakyThrows
    public static void beforeMethod() {
        System.setProperty("dw." + "server.applicationConnectors[0].port", "0");
        System.setProperty("dw." + "server.adminConnectors[0].port", "0");
        app.before();
        rabbitMQContainer();
        val config = getRMQConfig();
        connectionRegistry = new  ConnectionRegistry(app.getEnvironment(),
                (name, coreSize) -> Executors.newFixedThreadPool(coreSize),  config, TtlConfig.builder().build());
        connectionRegistry.start();
        rmqConnection = new RMQConnection("test-conn", getRMQConfig(),
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build());
        rmqConnection.start();
        hazelcastInstance = getHazelcastInstance();
        testBucket = hazelcastInstance.getMap("test");
    }

    @AfterAll
    @SneakyThrows
    public static void afterMethod() {
        app.after();
        rabbitMQ.stop();
        hazelcastInstance.shutdown();
    }

    @Test
    public void testRateLimitedActorPublish() throws Exception {
        val actorConfig = actorConfig();
        val publisher = new UnmanagedPublisher<>(queueName, actorConfig, rmqConnection, objectMapper);
        publisher.start();
        val message = ImmutableMap.of("key", "value");
        publisher.publish(message);
        val consumer = rateLimitedActor(10, 1, 1);
        consumer.start();
        Thread.sleep(1000);
        assertEquals(1, consumer.getCounter().get());
        consumer.stop();
        publisher.stop();
    }

    @Test
    public void testRateLimitedActorMultiplePublish() throws Exception {
        val actorConfig = actorConfig();
        val publisher = new UnmanagedPublisher<>(queueName, actorConfig, rmqConnection, objectMapper);
        publisher.start();
        val message = ImmutableMap.of("key", "value");
        for (int i = 0; i < 20; i++) {
            publisher.publish(message);
        }
        val consumer = rateLimitedActor(1, 1, 60);
        consumer.start();
        Thread.sleep(100);
        consumer.stop();
        assertTrue(consumer.getCounter().get() < 15);
        publisher.stop();
    }


    private static void rabbitMQContainer() {
        final var rabbitMqContainerConfig = new RabbitMQContainerConfiguration();
        rabbitMqContainerConfig.setDockerImage(RABBITMQ_DOCKER_IMAGE);
        rabbitMqContainerConfig.setWaitTimeoutInSeconds(300L);
        rabbitMqContainerConfig.setUser("admin");
        rabbitMqContainerConfig.setPassword("admin");
        rabbitMQ = new RabbitMQContainer(rabbitMqContainerConfig);
        rabbitMQ.start();
        log.info("RabbitMQ Container Started on Host: {} Port: {}",
                rabbitMQ.getHost(), rabbitMQ.getConnectionPort());
    }

    private static RMQConfig getRMQConfig() {
        val rmqConfig = new RMQConfig();
        val mappedPort = rabbitMQ.getMappedPort(5672);
        val host = rabbitMQ.getHost();
        val brokers = new ArrayList<Broker>();
        brokers.add(new Broker(host, mappedPort));
        rmqConfig.setBrokers(brokers);
        rmqConfig.setUserName(RABBITMQ_USERNAME);
        rmqConfig.setPassword(RABBITMQ_PASSWORD);
        rmqConfig.setVirtualHost("/");
        rmqConfig.setThreadPoolSize(2);
        log.info("RabbitMQ connection details: {}", rmqConfig);
        return rmqConfig;
    }

    private RateLimitConfiguration getRateLimitConfiguration() {
        return RateLimitConfiguration.builder()
                .bucketName("test")
                .rateLimit(10)
                .rateLimitPeriod(1)
                .rateLimitPeriodUnit(ChronoUnit.SECONDS)
                .refillRate(10)
                .rateLimitType(RateLimitConfiguration.RateLimitType.THROTTLE)
                .build();
    }

    private static HazelcastInstance getHazelcastInstance() throws UnknownHostException {
        Config config = new Config();
        config.setProperty("hazelcast.socket.client.bind.any", "true");
        config.setProperty("hazelcast.socket.bind.any", "true");
        config.getJetConfig().setEnabled(true);
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.getInterfaces().addInterface(InetAddress.getLocalHost().getHostAddress()).setEnabled(true);
        return Hazelcast.newHazelcastInstance(config);
    }

    private ActorConfig actorConfig() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        actorConfig.setConcurrency(1);
        actorConfig.setPrefetchCount(1);
        return actorConfig;
    }

    private TestRateLimitedActor rateLimitedActor(int rateLimit, int refillRate, int refillPeriod) {
        val config = getRateLimitConfiguration();
        config.setRateLimit(rateLimit);
        config.setRefillRate(refillRate);
        config.setRateLimitPeriod(refillPeriod);
        return new TestRateLimitedActor(actorConfig(), connectionRegistry, objectMapper,
                new RetryStrategyFactory(), new ExceptionHandlingFactory(), Map.class, Collections.emptySet(),
                getRateLimitConfiguration(), testBucket);
    }


}
