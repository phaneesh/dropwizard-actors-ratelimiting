# Rate limit extension for Dropwizard RabbitMQ Actors Bundle
Provides rate limiting capability over [dropwizard rabbitmq actors bundle](https://github.com/santanusinha/dropwizard-rabbitmq-actors).

## Dependency

```xml
<dependency>
    <groupId>com.github.phaneesh</groupId>
    <artifactId>dropwizard-actors-ratelimit</artifactId>
    <version>2.0.28-1</version>
</dependency>
```

## License
Apache-2.0

## Configuration
```java
RateLimitConfiguration rateLimitConfiguration = RateLimitConfiguration.builder()
        .bucketName("test")
        .rateLimit(100)
        .rateLimitPeriod(1)
        .rateLimitPeriodUnit(TimeUnit.SECONDS)
        .refillRate(10)
        .rateLimitType(RateLimitType.THROTTLE)
        .build();
```

## Note
The extension uses [bucket4j](https://github.com/bucket4j/bucket4j) to provide distributed rate limiting capability. Hazelcast is used to enable distributed rate limiting.
