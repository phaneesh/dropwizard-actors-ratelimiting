package com.github.phaneesh.actors.ratelimit;

import lombok.*;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RateLimitConfiguration {

    public enum RateLimitType {
        THROTTLE,
        REJECT
    }

    private String bucketName;

    private int rateLimit;

    @Builder.Default
    private int rateLimitPeriod = 1;

    @Builder.Default
    private TemporalUnit rateLimitPeriodUnit = ChronoUnit.SECONDS;

    private int refillRate;

    @Builder.Default
    private RateLimitType rateLimitType = RateLimitType.THROTTLE;

}
