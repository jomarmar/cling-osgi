/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.fourthline.cling.osgi.basedriver.present;

/*
 * UPnPEventHandler captures OSGi UPnP events. When handling a
 * event it compares all the registered UPnPEvent listeners
 * against the source of the event. If a listener matches the
 * source it will notify that listener.
 */

import java.util.*;
import java.util.logging.Logger;

import org.osgi.framework.Filter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPEventListener;
import org.osgi.service.upnp.UPnPService;

@Component (
		immediate = true,
        property = {EventConstants.EVENT_TOPIC+"=org/osgi/service/upnp/UPnPEvent"}
)
public class UPnPEventHandler implements EventHandler {
    private static final Logger logger = Logger.getLogger(UPnPEventHandler.class.getName());
	private static final Map<UPnPEventListener, Map<String, Object>> upnpEventListeners = new HashMap<>();


	@Reference(name = "upnpEventListener", service = UPnPEventListener.class,
            cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	public void bindUPnPListener(UPnPEventListener listener, Map<String, Object> props) {
		if(listener != null) {
			upnpEventListeners.put(listener, props);
		}
	}

    public void unbindUPnPListener(UPnPEventListener listener) {
		if(listener != null) {
			upnpEventListeners.remove(listener);
		}
    }

	@Override
	public void handleEvent(Event event) {
		logger.entering(this.getClass().getName(), "handleEvent", new Object[] { event });

		for(UPnPEventListener lst : upnpEventListeners.keySet()) {
			boolean matches = true;
			Map<String, Object> props = upnpEventListeners.get(lst);
			Filter filter = (Filter) props.get(UPnPEventListener.UPNP_FILTER);
			if (filter != null) {
				matches = event.matches(filter);
			}

			if (matches) {
				lst.notifyUPnPEvent(
						(String) event.getProperty(UPnPDevice.UDN),
						(String) event.getProperty(UPnPService.ID),
						(Dictionary) event.getProperty("upnp.events")
				);
			}
		}
	}
}

