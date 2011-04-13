package org.sothis.web.mvc.views;

import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.sothis.web.mvc.ActionInvocation;
import org.sothis.web.mvc.util.UrlUtils;

public class RedirectView extends AbstractView {

	@Override
	public void render(Object model, ActionInvocation invocation)
			throws Exception {
		HttpServletResponse response = invocation.getInvocationContext()
				.getResponse();
		String location = String.valueOf(params.get("location"));
		response.sendRedirect(UrlUtils
				.appendParams(location, (Map<?, ?>) model));
	}

}