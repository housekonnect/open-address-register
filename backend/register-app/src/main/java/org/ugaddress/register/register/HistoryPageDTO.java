package org.ugaddress.register.register;

import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One page of an object's recorded history, newest first.
 *
 * @param items history entries
 * @param nextCursor cursor of the next page, or {@code null} on the last page
 */
public record HistoryPageDTO(List<Entry> items, @Nullable String nextCursor) {

    /**
     * Creates the page.
     *
     * @param items items
     * @param nextCursor next cursor
     */
    public HistoryPageDTO {
        items = List.copyOf(items);
    }

    /**
     * One recorded version of the object.
     *
     * @param recordedAt when the change was recorded
     * @param operation {@code insert}, {@code update} or {@code delete}
     * @param version row version after the change
     * @param lifecycle lifecycle after the change
     * @param validFrom business validity start
     * @param validTo business validity end
     */
    public record Entry(OffsetDateTime recordedAt, String operation, long version, String lifecycle,
                        @Nullable OffsetDateTime validFrom, @Nullable OffsetDateTime validTo) {
    }
}
