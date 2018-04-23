package com.n26.restful.api;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

/**
 * Jersey general piping goes below.
 *
 * @author Andrew Polyakov
 */
@Component
public class JerseyConfig extends ResourceConfig {

	public JerseyConfig() {
		register(StatisticsEndpoint.class);
		register(TransactionsEndpoint.class);
		register(new AppExceptionMapper());
	}

}
