package fi.csc.chipster.rest.exception;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fi.csc.chipster.filestorage.UploadCancelledException;

public class ExceptionServletFilter implements Filter {
	
	private static final Logger logger = LogManager.getLogger();

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
			throws IOException, ServletException {	
		
		HttpServletRequest request = (HttpServletRequest)req;
		HttpServletResponse response = (HttpServletResponse)resp;
		
		try {
		
			chain.doFilter(request, response);
		} catch (ForbiddenException e) {
			logger.error("servlet error", e);
			sendError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			return;
		} catch (NotFoundException e) {
			logger.error("servlet error", e);
			sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
			return;
		} catch (UploadCancelledException e) {
			// logged already in FileServlet
			sendError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
			return;
		} catch (BadRequestException e) {
			logger.error("servlet error", e);
			sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			return;
		} catch (javax.ws.rs.NotAuthorizedException e) {
			logger.error("servlet error", e);
			sendError(response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
			return;
		} catch (ConflictException e) {
			logger.error("servlet error", e);
			sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
			return;				
		} catch (InsufficientStorageException e) {
						
			logger.error(e.getClass().getSimpleName() + " " + e.getMessage());
			try {
				// try to avoid "unconsumed input" error
				req.getInputStream().skip(Long.MAX_VALUE);
			} catch (Exception e2) {
				logger.warn("couldn't consume useless input", e2.getMessage());
			}
			
			sendError(response, InsufficientStorageException.STATUS_CODE, e.getMessage());
			
			return;	
		} catch (Exception e) {
			sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "servlet error");
			logger.error("servlet error", e);
			return;
		}
	}
	
	/**
	 * Send an HTTP error with plain text message
	 * 
	 * response.sendError() would send a html error, which is difficult use further
	 * 
	 * @param response
	 * @param statusCode
	 * @param message
	 * @throws IOException
	 */
	public void sendError(HttpServletResponse response, int statusCode, String message) throws IOException {
		response.setStatus(statusCode);
		try {
			response.getOutputStream().write(message.getBytes());
		} catch (IllegalStateException e) {
			// Jetty response can be in "STREAM" "WRITER" or undefined mode
			// What happens if something has been written already?
			response.getWriter().write(message);
		}
	}

	@Override
	public void destroy() {
	}
}
