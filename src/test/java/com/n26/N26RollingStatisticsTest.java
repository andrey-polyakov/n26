/**
 * Copyright 2015 Netflix, Inc.
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
package com.n26;

import org.junit.Test;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class N26RollingStatisticsTest {

    @Test
    public void testCreatesBuckets() {
        MockedTime time = new MockedTime();
        try {
            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);
            // confirm the initial settings
            assertEquals(200, counter.timeInMilliseconds);
            assertEquals(10l, counter.numberOfBuckets);
            assertEquals(20, counter.bucketSizeInMilliseconds);

            // we start out with 0 buckets in the queue
            assertEquals(0, counter.buckets.size());

            // add a success in each interval which should result in all 10l buckets being created with 1 success in each
            for (int i = 0; i < counter.numberOfBuckets; i++) {
                counter.addValue((long) 1);
                time.addValue(counter.bucketSizeInMilliseconds);
            }

            // confirm we have all 10l buckets
            assertEquals(10l, counter.buckets.size());

            // add 1 more and we should still only have 10l buckets since that's the aggregate
            counter.addValue((long) 1);
            assertEquals(10l, counter.buckets.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testResetBuckets() {
        MockedTime time = new MockedTime();
        try {
            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);

            // we start out with 0 buckets in the queue
            assertEquals(0, counter.buckets.size());

            // add 1
            counter.addValue(10l);

            // confirm we have 1 bucket
            assertEquals(1, counter.buckets.size());

            // confirm we still have 1 bucket
            assertEquals(1, counter.buckets.size());

            // add 1
            counter.addValue(10l);

            // we should now have a single bucket with no values in it instead of 2 or more buckets
            assertEquals(1, counter.buckets.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testEmptyBucketsFillIn() {
        MockedTime time = new MockedTime();
        try {
            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);

            // add 1
            counter.addValue(10l);

            // we should have 1 bucket
            assertEquals(1, counter.buckets.size());

            // wait past 3 bucket time periods (the 1st bucket then 2 empty ones)
            time.addValue(counter.bucketSizeInMilliseconds * 3);

            // add another
            counter.addValue(10l);

            // we should have 4 (1 + 2 empty + 1 new one) buckets
            assertEquals(4, counter.buckets.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testIncrementInSingleBucket() {
        MockedTime time = new MockedTime();
        try {
            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);

            // addValue
            counter.addValue(11l);
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(3l);
            counter.addValue(2l);
            counter.addValue(1l);

            // we should have 1 bucket
            assertEquals(1, counter.buckets.size());

            // the count should be 4
            assertEquals(47, counter.buckets.getLast().getSum().intValue());
            assertEquals(11, counter.buckets.getLast().getMax().intValue());
            assertEquals(7, counter.buckets.getLast().getCount().intValue());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testTimeout() {
        MockedTime time = new MockedTime();
        try {
            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);

            // addValue
            counter.addValue(-2323l);

            // we should have 1 bucket
            assertEquals(1, counter.buckets.size());

            // the count should be 1
            assertEquals(-2323l, counter.buckets.getLast().getSum().longValue());
            assertEquals(-2323l, counter.buckets.getLast().getMax().longValue());
            assertEquals(1l, counter.buckets.getLast().getCount().longValue());

            // sleep to get to a new bucket
            time.addValue(counter.bucketSizeInMilliseconds * 3);

            // incremenet again in latest bucket
            counter.addValue((long) 2);

            // we should have 4 buckets
            assertEquals(4, counter.buckets.size());

            // the counts of the last bucket
            assertEquals(1, counter.buckets.getLast().getCount().aggregate());

            // the total counts
            assertEquals(-2321, counter.getRolling().sum);

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testIncrementInMultipleBuckets() {
        MockedTime time = new MockedTime();
        try {
            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);

            // addValue
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(3);
            counter.addValue(3);
            counter.addValue(2);
            counter.addValue(2);

            // sleep to get to a new bucket
            time.addValue(counter.bucketSizeInMilliseconds * 3);

            // addValue
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(3);
            counter.addValue(3);
            counter.addValue(3);
            counter.addValue(2);

            // we should have 4 buckets
            assertEquals(4l, counter.buckets.size());

            // the counts of the last bucket
            assertEquals(31l, counter.buckets.getLast().getSum().longValue());
            assertEquals(10l, counter.buckets.getLast().getMax().longValue());
            assertEquals(2l, counter.buckets.getLast().getMin().longValue());
            assertEquals(6l, counter.buckets.getLast().getCount().longValue());

            // the total counts
            N26RollingStatistics.AggregatedStatistics rollOut = counter.getRolling();
            assertEquals(81l, rollOut.sum);
            assertEquals(10l, rollOut.max);
            assertEquals(2l, rollOut.min);
            assertEquals(14l, rollOut.size);

            // wait until window passes
            time.addValue(counter.timeInMilliseconds);

            // addValue
            counter.addValue(10l);

            // the total counts should now include only the last bucket after a reset since the window passed
            assertEquals(1l, counter.getRolling().size);
            assertEquals(10l, counter.getRolling().min);
            assertEquals(10l, counter.getRolling().max);

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testCounterRetrievalRefreshesBuckets() {
        MockedTime time = new MockedTime();
        try {
            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);

            // addValue
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(10l);
            counter.addValue(3);
            counter.addValue(3);

            // sleep to get to a new bucket
            time.addValue(counter.bucketSizeInMilliseconds * 3);

            // we should have 1 bucket since nothing has triggered the update of buckets in the elapsed time
            assertEquals(1, counter.buckets.size());

            // the total counts
            assertEquals(10, counter.getRolling().max);
            assertEquals(3, counter.getRolling().min);
            assertEquals(6, counter.getRolling().size);

            // we should have 4 buckets as the counter 'gets' should have triggered the buckets being created to fill in time
            assertEquals(4, counter.buckets.size());

            // wait until window passes
            time.addValue(counter.timeInMilliseconds);

            // the total counts should all be 0 (and the buckets cleared by the get, not only addValue)
            assertEquals(0, counter.getRolling().max);
            assertEquals(0, counter.getRolling().min);
            assertEquals(0, counter.getRolling().size);

            // addValue
            counter.addValue(10l);

            // the total counts should now include only the last bucket after a reset since the window passed
            assertEquals(10, counter.getRolling().max);
            assertEquals(10, counter.getRolling().min);
            assertEquals(1, counter.getRolling().size);

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testUpdateMax1() {
        MockedTime time = new MockedTime();
        try {
            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);

            // addValue
            counter.addValue(25000);

            // we should have 1 bucket
            assertEquals(1, counter.buckets.size());

            assertEquals(25000, counter.buckets.getLast().max.intValue());
            assertEquals(25000, counter.buckets.getLast().getSum().intValue());

            // sleep to get to a new bucket
            time.addValue(counter.bucketSizeInMilliseconds * 3);

            // addValue again in latest bucket
            counter.addValue(20);

            // we should have 4 buckets
            assertEquals(4, counter.buckets.size());

            // the aggregate
            assertEquals(20, counter.buckets.getLast().max.intValue());

            // counts per bucket
            Iterator<N26RollingStatistics.Bucket> i = counter.buckets.iterator();
            N26RollingStatistics.Bucket b1 = i.next();
            assertEquals(25000, b1.getMin().intValue()); // oldest bucket
            assertEquals(25000, b1.getMax().intValue()); // oldest bucket
            assertEquals(1, b1.getCount().intValue()); // oldest bucket
            N26RollingStatistics.Bucket b2 = i.next();
            assertEquals(0, b2.getCount().intValue());
            N26RollingStatistics.Bucket b3 = i.next();          // nothing in between
            assertEquals(0, b3.getCount().intValue());
            N26RollingStatistics.Bucket b4 = i.next();

            assertEquals(20, b4.getMin().intValue()); // latest bucket
            assertEquals(20, b4.getSum().intValue()); // latest bucket
            assertEquals(20, b4.getMax().intValue()); // latest bucket
            assertEquals(1, b4.getCount().intValue()); // latest bucket

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testMaxValue() {
        MockedTime time = new MockedTime();
        try {

            N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);


            // sleep to get to a new bucket
            time.addValue(counter.bucketSizeInMilliseconds);

            counter.addValue(30);

            // sleep to get to a new bucket
            time.addValue(counter.bucketSizeInMilliseconds);

            counter.addValue(40);

            // sleep to get to a new bucket
            time.addValue(counter.bucketSizeInMilliseconds);

            counter.addValue(15);

            assertEquals(40, counter.getRolling().max);

        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }
    }

    @Test
    public void testEmpty() {
        MockedTime time = new MockedTime();
        N26RollingStatistics counter = new N26RollingStatistics(time, 200, 10);
        assertEquals(0, counter.getRolling().max);
        assertEquals(0, counter.getRolling().min);
        assertEquals(0, counter.getRolling().size);
        assertEquals(0, counter.getRolling().sum);
        assertEquals(0, counter.getRolling().avg, 0.000001);
    }


    @Test
    public void testRolling() {
        MockedTime time = new MockedTime();
        N26RollingStatistics counter = new N26RollingStatistics(time, 20, 2);
        // iterate over 20 buckets on a queue sized for 2
        for (int i = 0; i < 20; i++) {
            // first bucket
            counter.getCurrentBucket();
            try {
                time.addValue(counter.bucketSizeInMilliseconds);
            } catch (Exception e) {
                // ignore
            }
            assertEquals(0, counter.getCurrentBucket().getCount().aggregate());
        }
    }

    private static class MockedTime implements N26RollingStatistics.Time {

        private AtomicInteger time = new AtomicInteger(0);

        public long getCurrentTimeInMillis() {
            return time.get();
        }

        public void addValue(int millis) {
            time.addAndGet(millis);
        }

    }

}
