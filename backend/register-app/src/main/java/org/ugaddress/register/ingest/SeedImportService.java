package org.ugaddress.register.ingest;

import java.util.List;
import org.springframework.stereotype.Service;
import org.ugaddress.register.shared.ProblemException;

/**
 * Imports existing address data (e.g. local government street lists or plot registers) into the register.
 *
 * <p>Stub: bulk imports will use Spring Batch and write through the workflow and audit modules. Today the only seed
 * data is the synthetic fixture loaded by Flyway in the {@code demo} profile.
 */
@Service
public class SeedImportService {

    /**
     * Creates the service.
     */
    public SeedImportService() {
    }

    /**
     * Lists the import sources the register understands.
     *
     * @return supported source formats; empty until the first importer exists
     */
    public List<String> supportedSources() {
        return List.of();
    }

    /**
     * Imports a seed dataset.
     *
     * @param source source format
     * @throws ProblemException always, with status 501, until importers exist
     */
    public void importSeed(final String source) {
        throw ProblemException.notImplemented("importSeed:" + source);
    }
}
