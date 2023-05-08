package com.github.phaneesh.actors.ratelimit;

import io.github.bucket4j.TokensInheritanceStrategy;
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

    private int refillPeriod = 1;

    private TemporalUnit refillPeriodUnit = ChronoUnit.SECONDS;

    @Builder.Default
    private RateLimitType rateLimitType = RateLimitType.THROTTLE;

    @Builder.Default
    private TokensInheritanceStrategy tokensInheritanceStrategy = TokensInheritanceStrategy.AS_IS;

}
