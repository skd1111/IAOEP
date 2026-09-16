package io.iaoep.sdk.abtest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A/B Test 自动装配 (Java SDK).
 *
 * <p>启用: {@code iaoep.sdk.abtest.enabled=true} (默认 true).</p>
 *
 * <p>用法:
 * <pre>
 * &#64;Autowired
 * private ABTestClient abTestClient;
 *
 * String group = abTestClient.assign("customer-service-ab", projectId, userId);
 * </pre>
 */
@Configuration
@ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
@ConditionalOnProperty(prefix = "iaoep.sdk.abtest", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ABTestAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ABTestClient abTestClient() {
        return new ABTestClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public ABTestAspect abTestAspect(ABTestClient client) {
        return new ABTestAspect(client);
    }
}
