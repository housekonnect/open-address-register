package org.ugaddress.register.workflow;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of the approver inbox, oldest first.
 *
 * @param items the change requests
 * @param nextCursor cursor of the next page, or {@code null} on the last page
 */
public record ChangeRequestPageDTO(List<ChangeRequestViewDTO> items, @Nullable String nextCursor) {

    /**
     * Creates the page.
     *
     * @param items items
     * @param nextCursor next cursor
     */
    public ChangeRequestPageDTO {
        items = List.copyOf(items);
    }
}
