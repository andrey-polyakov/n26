package com.n26.restful.api;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Restful API error handler.
 * @author Andrew Polyakov
 */
@Provider
public class AppExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof RuntimeException) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorPojo("Failed to process this request. Details: " + ex.getLocalizedMessage()))
                    .type(MediaType.APPLICATION_JSON).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorPojo(ex.getLocalizedMessage()))
                .type(MediaType.APPLICATION_JSON).build();
    }

    class ErrorPojo {
        String error;

        public ErrorPojo(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }

}
