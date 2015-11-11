package org.jemz.core.upnp.subscriber.internal;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 * Created by jmartinez on 10/19/15.
 */
@Component
public class TestEventListener implements EventHandler {

    @Override
    public void handleEvent(Event event) {
        System.out.println("EVENT >> " + event.getTopic());
    }
}
