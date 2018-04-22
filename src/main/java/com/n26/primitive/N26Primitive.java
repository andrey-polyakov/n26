package com.n26.primitive;

import java.io.Serializable;

public abstract class N26Primitive extends Striped64 implements Serializable {


    protected abstract long fn(long v, long x);

    /**
     * Returns the String representation of the {@link #aggregate}.
     * @return the String representation of the {@link #aggregate}
     */
    public String toString() {
        return Long.toString(aggregate());
    }

    /**
     * Equivalent to {@link #aggregate}.
     *
     * @return the maximum
     */
    public long longValue() {
        return aggregate();
    }

    /**
     * Returns the {@link #aggregate} as an {@code int} after a narrowing
     * primitive conversion.
     */
    public int intValue() {
        return (int) aggregate();
    }

    /**
     * Returns the {@link #aggregate} as a {@code float}
     * after a widening primitive conversion.
     */
    public float floatValue() {
        return (float) aggregate();
    }

    /**
     * Returns the {@link #aggregate} as a {@code double} after a widening
     * primitive conversion.
     */
    public double doubleValue() {
        return (double) aggregate();
    }

    public abstract long aggregate();

    /**
     * Sets base and all cells to the given value.
     */
    public final void internalReset(long initialValue) {
        Cell[] as = cells;
        base = initialValue;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null)
                    a.value = initialValue;
            }
        }
    }

    /**
     * Equivalent in effect to {@link #aggregate} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     *
     * @return the maximum
     */
    public abstract long getThenReset();

    /**
     * This method may be a useful alternative to creating a new
     * updater, but is only effective if there are no concurrent
     * updates.  Because this method is intrinsically racy, it should
     * only be used when it is known that no threads are concurrently
     * updating.
     */
    public abstract void reset();

}
