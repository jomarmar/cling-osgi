package org.jemz.core.ws;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;

/**
 * Created by jmartinez on 11/18/15.
 */
@Component(immediate = true,
        service = { Servlet.class },
        property = { "alias:String=/stock" }
)
public class StockServiceServlet extends WebSocketServlet {
    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.register(JFWebsocket.class);
    }
}
