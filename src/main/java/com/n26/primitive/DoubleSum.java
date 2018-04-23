package com.n26.primitive;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * CAS based primitive for summations.Based on LongAdder from Hystrix.
 *
 * @author Andrew Polyakov
 */
public class DoubleSum extends N26DoublePrimitive implements Serializable {

    /**
     * Creates a new aggregator with initial values of zero.
     */
    public DoubleSum() {
        base = Double.valueOf(0.0);
    }

    /**
     * Creates a new aggregator with preset initial value.
     * @param setBase
     */
    public DoubleSum(Double setBase) {
        base = setBase;
    }

    /**
     * Adds the given value to the sum.
     *
     * @param x the value to add
     */
    public void add(Double x) {
        Cell[] as; Double b, v; HashCode hc; Cell a; int n;
        if ((as = cells) != null || !casBase(b = base, b + x)) {
            boolean uncontended = true;
            int h = (hc = threadHashCode.get()).code;
            if (as == null || (n = as.length) < 1 ||
                    (a = as[(n - 1) & h]) == null ||
                    !(uncontended = a.cas(v = a.value, v + x)))
                retryUpdate(x, hc, uncontended);
        }
    }


    /**
     * Summation formula defined here and called on retries whenever CAS write fails.
     * @param v
     * @param x
     * @return
     */
    @Override
    protected double fn(Double v, Double x) {
        return v + x;
    }

    /**
     * Returns the current sum. The returned value is <em>NOT</em> an
     * atomic snapshot: Invocation in the absence of concurrent
     * updates returns an accurate result, but concurrent updates that
     * occur while the min is being calculated might not be
     * incorporated.
     *
     * @return the sum
     */
    @Override
    public double aggregate() {
        double sum = base;
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
     * @return the aggregate value
     */
    @Override
    public double getThenReset() {
        Double sum = base;
        Cell[] as = cells;
        base = 0.0;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null) {
                    sum += a.value;
                    a.value = 0.0;
                }
            }
        }
        return sum;
    }

    /**
     * To support serialization
     * @param s
     * @throws java.io.IOException
     */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        s.writeDouble(aggregate());
    }

    /**
     * To support serialization
     * @param s
     * @throws java.io.IOException
     */
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        busy = 0;
        cells = null;
        base = s.readDouble();
    }

}
