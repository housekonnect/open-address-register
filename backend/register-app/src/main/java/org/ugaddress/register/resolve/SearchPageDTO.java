package org.ugaddress.register.resolve;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.ugaddress.register.register.AddressableObjectDTO;

/**
 * One page of search results, best match first.
 *
 * @param items the matching objects, redacted for the caller
 * @param nextCursor cursor of the next page, or {@code null} on the last page
 */
public record SearchPageDTO(List<AddressableObjectDTO> items, @Nullable String nextCursor) {

    /**
     * Creates the page.
     *
     * @param items items
     * @param nextCursor next cursor
     */
    public SearchPageDTO {
        items = List.copyOf(items);
    }
}
