package org.ugaddress.register;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for integration tests: the full application against real PostGIS and Record Store, with the synthetic
 * fixtures loaded by Flyway ({@code demo} profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void infrastructure(final DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }
}
