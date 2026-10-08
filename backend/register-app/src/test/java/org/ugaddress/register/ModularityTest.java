package org.ugaddress.register;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the module structure: no cycles, and no module uses another module's internal types.
 */
class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(RegisterApplication.class);

    @Test
    void modulesOnlyUseEachOthersPublicApi() {
        // GIVEN the application's modules
        // WHEN their dependencies are verified
        // THEN there are no cycles and no access to internal packages
        MODULES.verify();
    }

    @Test
    void containsTheExpectedModules() {
        // GIVEN the application's modules
        // WHEN they are listed
        // THEN exactly the planned modules exist
        assertThat(MODULES.stream().map(m -> m.getIdentifier().toString()))
            .containsExactlyInAnyOrder("shared", "audit", "gazetteer", "register", "resolve", "workflow", "field",
                "ingest");
    }

    @Test
    void writesModuleDocumentation() {
        // GIVEN the application's modules
        // WHEN documentation is generated
        // THEN PlantUML diagrams and module canvases are written to target/spring-modulith-docs
        new Documenter(MODULES).writeDocumentation();
    }
}
