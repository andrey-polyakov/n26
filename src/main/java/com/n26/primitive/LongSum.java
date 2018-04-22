package com.n26.primitive;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

public class LongSum extends N26Primitive implements Serializable {


    /**
     * Creates a new aggregator with initial values of zero.
     */
    public LongSum() {
    }

    /**
     * Adds the given value.
     *
     * @param x the value to add
     */
    public void add(long x) {
        Cell[] as; long b, v; HashCode hc; Cell a; int n;
        if ((as = cells) != null || !casBase(b = base, b + x)) {
            boolean uncontended = true;
            int h = (hc = threadHashCode.get()).code;
            if (as == null || (n = as.length) < 1 ||
                    (a = as[(n - 1) & h]) == null ||
                    !(uncontended = a.cas(v = a.value, v + x)))
                retryUpdate(x, hc, uncontended);
        }
    }


    @Override
    protected long fn(long v, long x) {
        return v + x;
    }

    /**
     * Returns the current min.  The returned value is <em>NOT</em> an
     * atomic snapshot: Invocation in the absence of concurrent
     * updates returns an accurate result, but concurrent updates that
     * occur while the min is being calculated might not be
     * incorporated.
     *
     * @return the min
     */
    @Override
    public long aggregate() {
        long sum = base;
        Cell[] as = cells;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null)
                    sum += a.value;
            }
        }
        return sum;
    }

    /**
     * Resets variables maintaining the min to zero.  This method may
     * be a useful alternative to creating a new adder, but is only
     * effective if there are no concurrent updates.  Because this
     * method is intrinsically racy, it should only be used when it is
     * known that no threads are concurrently updating.
     */
    @Override
    public void reset() {
        internalReset(0L);
    }

    /**
     * Equivalent in effect to {@link #aggregate} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     *
     * @return the min
     */
    @Override
    public long getThenReset() {
        long sum = base;
        Cell[] as = cells;
        base = 0L;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null) {
                    sum += a.value;
                    a.value = 0L;
                }
            }
        }
        return sum;
    }

    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        s.defaultWriteObject();
        s.writeLong(aggregate());
    }

    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        busy = 0;
        cells = null;
        base = s.readLong();
    }

}
