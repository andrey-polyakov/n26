package com.n26;
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

import com.n26.primitive.DoubleMax;
import com.n26.primitive.DoubleMin;
import com.n26.primitive.DoubleSum;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.StrictMath.max;
import static java.lang.StrictMath.min;

/**
 * Time aware rolling statistics. Thread safe, uses custom CAS based primitives. Some functionality borrowed from
 * Netflix stack, other things added and adjusted.
 *
 *<br>
 * Specifically, inspiration for this class is the class called HystrixRollingNumber from Hystrix framework.
 *
 * @author Andrew Polyakov
 *
 */
public class N26RollingStatistics {

    private static final N26RollingStatistics.Time ACTUAL_TIME = new N26RollingStatistics.ActualTime();
    final N26RollingStatistics.Time time;
    final int timeInMilliseconds;
    final int numberOfBuckets;
    final int bucketSizeInMilliseconds;

    final N26RollingStatistics.BucketCircularArray buckets;


    public N26RollingStatistics(int timeInMilliseconds, int numberOfBuckets) {
        this(ACTUAL_TIME, timeInMilliseconds, numberOfBuckets);
    }

    /* package for testing */ N26RollingStatistics(N26RollingStatistics.Time time, int timeInMilliseconds, int numberOfBuckets) {
        this.time = time;
        this.timeInMilliseconds = timeInMilliseconds;
        this.numberOfBuckets = numberOfBuckets;

        if (timeInMilliseconds % numberOfBuckets != 0) {
            throw new IllegalArgumentException("The timeInMilliseconds must divide equally into numberOfBuckets. For example 1000/10 is ok, 1000/11 is not.");
        }
        this.bucketSizeInMilliseconds = timeInMilliseconds / numberOfBuckets;

        buckets = new N26RollingStatistics.BucketCircularArray(numberOfBuckets);
    }

    public boolean addValue(Double value, Long timestamp) {
        long windowToCapture = time.getCurrentTimeInMillis() - timeInMilliseconds;

        if (new Timestamp(timestamp).after(new Timestamp(windowToCapture))) {
            addValue(value);
            return true;
        } else {
            return false;
        }
    }

    public void addValue(Double value) {
        Bucket bucket = getCurrentBucket();
        bucket.max.update(value);
        bucket.min.update(value);
        bucket.sum.add(value);
        bucket.count.add(1.0);
    }

    /**
     * Force a reset of all rolling counters (clear all buckets) so that statistics start being gathered from scratch.
     */
    public void reset() {
        buckets.clear();
    }

    public static final AggregatedStatistics EMPTY = new AggregatedStatistics(0, 0, 0, 0);


    /**
     * This is key method which produces rolling statistics. It aggregates numbers from all the buckets.
     * Each bucket represents a time frame.
     *
     *<br>
     * Please note when under contention the numbers will lag. Eventually things will align with reality.
     *
     * @return POJO with statistics for current window
     */
    public AggregatedStatistics getRolling() {
        N26RollingStatistics.Bucket lastBucket = getCurrentBucket();
        if (lastBucket == null)
            return EMPTY;
        Double sum = 0.0;
        Double min = null;
        Double max = null;
        long size = 0;
        for (N26RollingStatistics.Bucket b : buckets) {
            double sizeOfThisBucket = b.count.aggregate();
            if (sizeOfThisBucket < 1) {
                continue;// Empty one, skip it
            }
            size += sizeOfThisBucket;
            sum += b.sum.aggregate();
            if (max == null) {
                max = b.max.aggregate();
            } else {
                max = max(max, b.max.aggregate());
            }
            if (min == null) {
                min = b.min.aggregate();
            } else {
                min = min(min, b.min.aggregate());
            }
        }
        if (size == 0) {
            return EMPTY;
        }
        return new AggregatedStatistics(size, sum, min, max);
    }

    /**
     * This is just a POJO with aggregated values.
     *
     * @author Andrew Polyakov
     */
    public static class AggregatedStatistics {
        final long size;
        final double sum;
        final double avg;
        final double min;
        final double max;

        public AggregatedStatistics(long size, double sum, double min, double max) {
            this.size = size;
            this.sum = sum;
            this.min = min;
            this.max = max;
            if ( size != 0) {
                avg = sum / size;
            } else {
                avg = 0.0;
            }
        }

        public long getSize() {
            return size;
        }

        public double getSum() {
            return sum;
        }

        public double getAvg() {
            return avg;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }
    }

    private ReentrantLock newBucketLock = new ReentrantLock();

    /* package for testing */N26RollingStatistics.Bucket getCurrentBucket() {
        long currentTime = time.getCurrentTimeInMillis();

        /* a shortcut to try and get the most common result of immediately finding the current bucket */

        /**
         * Retrieve the latest bucket if the given time is BEFORE the end of the bucket window, otherwise it returns NULL.
         *
         * NOTE: This is thread-safe because it's accessing 'buckets' which is a LinkedBlockingDeque
         */
        N26RollingStatistics.Bucket currentBucket = buckets.peekLast();
        if (currentBucket != null && currentTime < currentBucket.windowStart + this.bucketSizeInMilliseconds) {
            // if we're within the bucket 'window of time' return the current one
            // NOTE: We do not worry if we are BEFORE the window in a weird case of where thread scheduling causes that to occur,
            // we'll just use the latest as long as we're not AFTER the window
            return currentBucket;
        }

        /* if we didn't find the current bucket above, then we have to create one */

        /**
         * The following needs to be synchronized/locked even with a synchronized/thread-safe data structure such as LinkedBlockingDeque because
         * the logic involves multiple steps to check existence, create an object then insert the object. The 'check' or 'insertion' themselves
         * are thread-safe by themselves but not the aggregate algorithm, thus we put this entire block of logic inside synchronized.
         *
         * I am using a tryLock if/then (http://download.oracle.com/javase/6/docs/api/java/util/concurrent/locks/Lock.html#tryLock())
         * so that a single thread will get the lock and as soon as one thread gets the lock all others will go the 'else' block
         * and just return the currentBucket until the newBucket is created. This should allow the throughput to be far higher
         * and only slow down 1 thread instead of blocking all of them in each cycle of creating a new bucket based on some testing
         * (and it makes sense that it should as well).
         *
         * This means the timing won't be exact to the millisecond as to what data ends up in a bucket, but that's acceptable.
         * It's not critical to have exact precision to the millisecond, as long as it's rolling, if we can instead reduce the impact synchronization.
         *
         * More importantly though it means that the 'if' block within the lock needs to be careful about what it changes that can still
         * be accessed concurrently in the 'else' block since we're not completely synchronizing access.
         *
         * For example, we can't have a multi-step process to add a bucket, remove a bucket, then update the min since the 'else' block of code
         * can retrieve the min while this is all happening. The trade-off is that we don't maintain the rolling min and let readers just iterate
         * bucket to calculate the min themselves. This is an example of favoring write-performance instead of read-performance and how the tryLock
         * versus a synchronized block needs to be accommodated.
         */
        if (newBucketLock.tryLock()) {
            try {
                if (buckets.peekLast() == null) {
                    // the list is empty so create the first bucket
                    N26RollingStatistics.Bucket newBucket = new N26RollingStatistics.Bucket(currentTime);
                    buckets.addLast(newBucket);
                    return newBucket;
                } else {
                    // We go into a loop so that it will create as many buckets as needed to catch up to the current time
                    // as we want the buckets complete even if we don't have transactions during a period of time.
                    for (int i = 0; i < numberOfBuckets; i++) {
                        // we have at least 1 bucket so retrieve it
                        N26RollingStatistics.Bucket lastBucket = buckets.peekLast();
                        if (currentTime < lastBucket.windowStart + this.bucketSizeInMilliseconds) {
                            // if we're within the bucket 'window of time' return the current one
                            // NOTE: We do not worry if we are BEFORE the window in a weird case of where thread scheduling causes that to occur,
                            // we'll just use the latest as long as we're not AFTER the window
                            return lastBucket;
                        } else if (currentTime - (lastBucket.windowStart + this.bucketSizeInMilliseconds) > timeInMilliseconds) {
                            // the time passed is greater than the entire rolling counter so we want to clear it all and start from scratch
                            reset();
                            // recursively call getCurrentBucket which will create a new bucket and return it
                            return getCurrentBucket();
                        } else { // we're past the window so we need to create a new bucket
                            // create a new bucket and add it as the new 'last'
                            buckets.addLast(new N26RollingStatistics.Bucket(lastBucket.windowStart + this.bucketSizeInMilliseconds));
                        }
                    }
                    // we have finished the for-loop and created all of the buckets, so return the lastBucket now
                    return buckets.peekLast();
                }
            } finally {
                newBucketLock.unlock();
            }
        } else {
            currentBucket = buckets.peekLast();
            if (currentBucket != null) {
                // we didn't get the lock so just return the latest bucket while another thread creates the next one
                return currentBucket;
            } else {
                // the rare scenario where multiple threads raced to create the very first bucket
                // wait slightly and then use recursion while the other thread finishes creating a bucket
                try {
                    Thread.sleep(5);
                } catch (Exception e) {
                    // ignore
                }
                return getCurrentBucket();
            }
        }
    }

    /* package */static interface Time {
        public long getCurrentTimeInMillis();
    }

    private static class ActualTime implements N26RollingStatistics.Time {

        public long getCurrentTimeInMillis() {
            return System.currentTimeMillis();
        }

    }

    /**
     * Counters for a given 'bucket' of time.
     */
    /* package */public static class Bucket {
        final long windowStart;
        final DoubleMax max = new DoubleMax();
        final DoubleSum sum = new DoubleSum();
        final DoubleMin min = new DoubleMin();
        final DoubleSum count = new DoubleSum();

        Bucket(long startTime) {
            this.windowStart = startTime;
        }

        DoubleMax getMax() {
            return max;
        }

        DoubleSum getSum() {
            return sum;
        }

        DoubleMin getMin() {
            return min;
        }

        public DoubleSum getCount() {
            return count;
        }

        public long getWindowStart() {
            return windowStart;
        }
    }

    /**
     * This is a circular array acting as a FIFO queue.
     * <p>
     * It purposefully does NOT implement Deque or some other Collection interface as it only implements functionality necessary for this RollingNumber use case.
     * <p>
     * Important Thread-Safety Note: This is ONLY thread-safe within the context of RollingNumber and the protection it gives in the <code>getCurrentBucket</code> method. It uses AtomicReference
     * objects to ensure anything done outside of <code>getCurrentBucket</code> is thread-safe, and to ensure visibility of changes across threads (ie. volatility) but the addLast and removeFirst
     * methods are NOT thread-safe for external access they depend upon the lock.tryLock() protection in <code>getCurrentBucket</code> which ensures only a single thread will access them at at time.
     * <p>
     * benjchristensen => This implementation was chosen based on performance testing I did and documented at: http://benjchristensen.com/2011/10/08/atomiccirculararray/
     */
    /* package */static class BucketCircularArray implements Iterable<N26RollingStatistics.Bucket> {
        private final AtomicReference<N26RollingStatistics.BucketCircularArray.ListState> state;
        private final int dataLength; // we don't resize, we always stay the same, so remember this
        private final int numBuckets;

        /**
         * Immutable object that is atomically set every time the state of the BucketCircularArray changes
         * <p>
         * This handles the compound operations
         */
        private class ListState {
            /*
             * this is an AtomicReferenceArray and not a normal Array because we're copying the reference
             * between ListState objects and multiple threads could maintain references across these
             * compound operations so I want the visibility/concurrency guarantees
             */
            private final AtomicReferenceArray<N26RollingStatistics.Bucket> data;
            private final int size;
            private final int tail;
            private final int head;

            private ListState(AtomicReferenceArray<N26RollingStatistics.Bucket> data, int head, int tail) {
                this.head = head;
                this.tail = tail;
                if (head == 0 && tail == 0) {
                    size = 0;
                } else {
                    this.size = (tail + dataLength - head) % dataLength;
                }
                this.data = data;
            }

            public N26RollingStatistics.Bucket tail() {
                if (size == 0) {
                    return null;
                } else {
                    // we want to get the last item, so size()-1
                    return data.get(convert(size - 1));
                }
            }

            private N26RollingStatistics.Bucket[] getArray() {
                /*
                 * this isn't technically thread-safe since it requires multiple reads on something that can change
                 * but since we never clear the data directly, only increment/decrement head/tail we would never get a NULL
                 * just potentially return stale data which we are okay with doing
                 */
                ArrayList<N26RollingStatistics.Bucket> array = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    array.add(data.get(convert(i)));
                }
                return array.toArray(new N26RollingStatistics.Bucket[array.size()]);
            }

            private N26RollingStatistics.BucketCircularArray.ListState incrementTail() {
                /* if incrementing results in growing larger than 'length' which is the aggregate we should be at, then also increment head (equivalent of removeFirst but done atomically) */
                if (size == numBuckets) {
                    // increment tail and head
                    return new N26RollingStatistics.BucketCircularArray.ListState(data, (head + 1) % dataLength, (tail + 1) % dataLength);
                } else {
                    // increment only tail
                    return new N26RollingStatistics.BucketCircularArray.ListState(data, head, (tail + 1) % dataLength);
                }
            }

            public N26RollingStatistics.BucketCircularArray.ListState clear() {
                return new N26RollingStatistics.BucketCircularArray.ListState(new AtomicReferenceArray<>(dataLength), 0, 0);
            }

            public N26RollingStatistics.BucketCircularArray.ListState addBucket(N26RollingStatistics.Bucket b) {
                /*
                 * We could in theory have 2 threads addBucket concurrently and this compound operation would interleave.
                 * <p>
                 * This should NOT happen since getCurrentBucket is supposed to be executed by a single thread.
                 * <p>
                 * If it does happen, it's not a huge deal as incrementTail() will be protected by compareAndSet and one of the two addBucket calls will succeed with one of the Buckets.
                 * <p>
                 * In either case, a single Bucket will be returned as "last" and data loss should not occur and everything keeps in sync for head/tail.
                 * <p>
                 * Also, it's fine to set it before incrementTail because nothing else should be referencing that index position until incrementTail occurs.
                 */
                data.set(tail, b);
                return incrementTail();
            }

            // The convert() method takes a logical index (as if head was
            // always 0) and calculates the index within elementData
            private int convert(int index) {
                return (index + head) % dataLength;
            }
        }

        BucketCircularArray(int size) {
            AtomicReferenceArray<N26RollingStatistics.Bucket> _buckets = new AtomicReferenceArray<N26RollingStatistics.Bucket>(size + 1); // + 1 as extra room for the add/remove;
            state = new AtomicReference<N26RollingStatistics.BucketCircularArray.ListState>(new N26RollingStatistics.BucketCircularArray.ListState(_buckets, 0, 0));
            dataLength = _buckets.length();
            numBuckets = size;
        }

        public void clear() {
            while (true) {
                /*
                 * it should be very hard to not succeed the first pass through since this is typically is only called from
                 * a single thread protected by a tryLock, but there is at least 1 other place (at time of writing this comment)
                 * where reset can be called from (CircuitBreaker.markSuccess after circuit was tripped) so it can
                 * in an edge-case conflict.
                 *
                 * Instead of trying to determine if someone already successfully called clear() and we should skip
                 * we will have both calls reset the circuit, even if that means losing data added in between the two
                 * depending on thread scheduling.
                 *
                 * The rare scenario in which that would occur, we'll accept the possible data loss while clearing it
                 * since the code has stated its desire to clear() anyways.
                 */
                //TODO cover with tests
                N26RollingStatistics.BucketCircularArray.ListState current = state.get();
                N26RollingStatistics.BucketCircularArray.ListState newState = current.clear();
                if (state.compareAndSet(current, newState)) {
                    return;
                }
            }
        }

        /**
         * Returns an iterator on a copy of the internal array so that the iterator won't fail by buckets being added/removed concurrently.
         */
        public Iterator<N26RollingStatistics.Bucket> iterator() {
            return Collections.unmodifiableList(Arrays.asList(getArray())).iterator();
        }

        public void addLast(N26RollingStatistics.Bucket o) {
            N26RollingStatistics.BucketCircularArray.ListState currentState = state.get();
            // create new version of state (what we want it to become)
            N26RollingStatistics.BucketCircularArray.ListState newState = currentState.addBucket(o);

            /*
             * use compareAndSet to set in case multiple threads are attempting (which shouldn't be the case because since addLast will ONLY be called by a single thread at a time due to protection
             * provided in <code>getCurrentBucket</code>)
             */
            if (state.compareAndSet(currentState, newState)) {
                // we succeeded
                return;
            } else {
                // we failed, someone else was adding or removing
                // instead of trying again and risking multiple addLast concurrently (which shouldn't be the case)
                // we'll just return and let the other thread 'win' and if the timing is off the next call to getCurrentBucket will fix things
                return;
            }
        }

        public N26RollingStatistics.Bucket getLast() {
            return peekLast();
        }

        public int size() {
            // the size can also be worked out each time as:
            // return (tail + data.length() - head) % data.length();
            return state.get().size;
        }

        public N26RollingStatistics.Bucket peekLast() {
            return state.get().tail();
        }

        private N26RollingStatistics.Bucket[] getArray() {
            return state.get().getArray();
        }

    }



}
