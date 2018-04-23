package com.n26.restful.api;

import com.n26.N26RollingStatistics;
import com.n26.restful.api.dto.TransactionDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * API for other services to push transactions.
 */
@Component
@Path("/transactions")
public class TransactionsEndpoint {

	private N26RollingStatistics rs;

	@Inject
	public TransactionsEndpoint(@Qualifier("rollingStatistics") N26RollingStatistics rs) {
		this.rs = rs;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response postTransaction(@RequestBody TransactionDto input) {
		if (rs.addValue(input.getAmount(), input.getTimestamp())) {
			return Response.status(201).build();
		} else {
			return Response.status(204).build();
		}
	}

}
