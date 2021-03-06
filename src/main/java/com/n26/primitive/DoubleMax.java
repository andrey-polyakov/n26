/**
 * Copyright 2012 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.n26.primitive;

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 *
 * From http://gee.cs.oswego.edu/cgi-bin/viewcvs.cgi/jsr166/src/jsr166e/
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Primitive maximum value aggregator based on CAS. Based on LongMaxUpdater from Hystrix.
 *
 * @author Andrew Polyakov
 */
public class DoubleMax extends N26DoublePrimitive implements Serializable {


    @Override
    protected double fn(Double v, Double x) {
        return v > x ? v : x;
    }


    public DoubleMax() {
        base = -Double.MAX_VALUE;
    }

    /**
     * Updates the maximum to be at least the given value.
     *
     * @param x the value to update
     */
    public void update(Double x) {
        Cell[] as;
        Double b, v;
        HashCode hc;
        Cell a;
        int n;
        if ((as = cells) != null || (b = base) < x && !casBase(b, x)) {
            boolean uncontended = true;
            int h = (hc = threadHashCode.get()).code;
            if (as == null || (n = as.length) < 1 ||
                    (a = as[(n - 1) & h]) == null ||
                    ((v = a.value) < x && !(uncontended = a.cas(v, x))))
                retryUpdate(x, hc, uncontended);
        }
    }

    /**
     * Returns the current maximum.  The returned value is
     * <em>NOT</em> an atomic snapshot: Invocation in the absence of
     * concurrent updates returns an accurate result, but concurrent
     * updates that occur while the value is being calculated might
     * not be incorporated.
     *
     * @return the maximum
     */
    @Override
    public double aggregate() {
        Cell[] as = cells;
        Double max = base;
        if (as != null) {
            int n = as.length;
            Double v;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null && (v = a.value) > max)
                    max = v;
            }
        }
        return max;
    }

    @Override
    public void reset() {
        internalReset(-Double.MAX_VALUE);
    }

    @Override
    public double getThenReset() {
        Cell[] as = cells;
        Double max = base;
        base =  -Double.MAX_VALUE;
        if (as != null) {
            int n = as.length;
            for (int i = 0; i < n; ++i) {
                Cell a = as[i];
                if (a != null) {
                    Double v = a.value;
                    a.value = -Double.MAX_VALUE;
                    if (v > max)
                        max = v;
                }
            }
        }
        return max;
    }

    private void writeObject(java.io.ObjectOutputStream s)
            throws IOException {
        s.defaultWriteObject();
        s.writeDouble(aggregate());
    }

    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        busy = 0;
        cells = null;
        base = s.readDouble();
    }

}
