package com.n26.restful.api;


import com.n26.N26RollingStatistics;
import com.n26.restful.api.dto.StatisticsDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This component exposes rolling statistics via Restful interface. There is a worker constantly refreshing
 * response to be returned. This is why get() method runs in O(1).
 *
 * @author Andrew Polyakov
 */
@Component
@Path("/statistics")
public class StatisticsEndpoint {

    private final ExecutorService e = Executors.newSingleThreadExecutor();

    public Deque<StatisticsDto> response =  new ConcurrentLinkedDeque<>();

    /**
     * The smaller the refreshInterval the sooner worker replaces data. Set this to something positive significantly
     * smaller than your window for optimal performance.
     */
    @Inject
    public StatisticsEndpoint(final Integer refreshInterval, @Qualifier("rollingStatistics") final N26RollingStatistics rs) {
        N26RollingStatistics.AggregatedStatistics initial = rs.getRolling();
        response.offerFirst(new StatisticsDto(initial.getSize(), // this is to ensure there is at least one value at all times
                initial.getMin(),
                initial.getMax(),
                initial.getAvg(),
                initial.getSum()));
        e.submit(() -> {
                while(true) {
                    /**
                     * To update statistics on a set interval. Worker continuously refreshes
                     * statistics. Queue is required to maintain visibility and ensure there is always a value.
                     */
                    Thread.sleep(refreshInterval);
                    N26RollingStatistics.AggregatedStatistics rollOut = rs.getRolling();
                    response.offerFirst(new StatisticsDto(rollOut.getSize(), // put the latest data at the first position
                            rollOut.getMin(),
                            rollOut.getMax(),
                            rollOut.getAvg(),
                            rollOut.getSum()));
                    response.removeLast(); // remove outdated object
                }
        });
    }

    /**
     * This method exposes statistics to readers.
     * @return latest computed result, not necessarily an up to date snapshot though.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public StatisticsDto get() {
        return response.getFirst(); // This runs in O(1) as everything is precomputed.
    }
}
