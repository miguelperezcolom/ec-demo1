package io.mateu.ecdemo1.mapping.queries;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Rows from {@code offset}, {@code size} of them — a window that does not start on a page boundary.
 * A listing whose first rows come from somewhere else (the dictionary's unmapped codes) asks the
 * database for the rest of the screen's page from where those end.
 */
record OffsetPageable(long offset, int size) implements Pageable {

    OffsetPageable {
        if (offset < 0 || size < 1) {
            throw new IllegalArgumentException("offset " + offset + ", size " + size);
        }
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / size);
    }

    @Override
    public int getPageSize() {
        return size;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return Sort.unsorted();
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + size, size);
    }

    @Override
    public Pageable previousOrFirst() {
        return new OffsetPageable(Math.max(0, offset - size), size);
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, size);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return PageRequest.of(pageNumber, size);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
