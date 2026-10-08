package org.ugaddress.register.audit.internal;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ugaddress.register.audit.AuditService;

/**
 * Background jobs of the audit module, stored and coordinated in PostgreSQL by db-scheduler.
 */
@Configuration(proxyBeanMethods = false)
class AuditJobsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AuditJobsConfig.class);

    /**
     * Re-computes the audit hash chain every hour and logs an error if it was tampered with.
     */
    @Bean
    RecurringTask<Void> auditChainVerificationTask(final AuditService auditService) {
        return Tasks.recurring("audit-chain-verification", Schedules.fixedDelay(Duration.ofHours(1)))
            .execute((instance, context) -> auditService.verifyChain().ifPresentOrElse(
                seq -> LOG.error("Audit chain is broken at event {}", seq),
                () -> LOG.info("Audit chain verified")));
    }
}
