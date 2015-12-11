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

package org.fourthline.cling.osgi.basedriver.discover;

import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.osgi.basedriver.impl.UPnPDeviceImpl;
import org.fourthline.cling.osgi.basedriver.impl.UPnPServiceImpl;
import org.fourthline.cling.osgi.basedriver.util.IClingBasedriver;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.annotations.*;
import org.osgi.service.upnp.UPnPEventListener;
import org.osgi.service.upnp.UPnPService;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;

import java.util.*;
import java.util.logging.Logger;

/**
 * Monitors and handles the addition and removal of remote devices.
 * <p>
 * When a device is added:
 * </p>
 * <ul>
 * <li>Wrap the device inside of a UPnPDevice implementation.</li>
 * <li>Create and open a UPnPEventListener tracker for that device.</li>
 * <li>Register the new UPnPDevice with the OSGi Framework.</li>
 * </ul>
 * <p>
 * When a device is removed:
 * </p>
 * <ul>
 * <li>Unregister the UPnPDevice with the OSGi Framework.</li>
 * <li>Close the UPnPEventListener tracker for that device.</li>
 * </ul>
 *
 * @author Bruce Green
 */

@Component ()
public class ClingRegistryListener extends DefaultRegistryListener {

    private static final Logger log = Logger.getLogger(ClingRegistryListener.class.getName());

    private UpnpService upnpService;
    private ComponentFactory factory;
    private ComponentInstance instance;

    private List<UPnPDeviceImpl> deviceList = new ArrayList<>();

    private Map<UPnPEventListener, List<SubscriptionCallback>> listenerCallbacks = new Hashtable();

    @Activate
    public void start() {
        upnpService.getControlPoint().getRegistry().addListener(this);
    }

    @Deactivate
    public void stop() {

    }

    @Reference(
            service = IClingBasedriver.class
    )
    public void bindUpnpService (IClingBasedriver service) {
        this.upnpService = service.getUpnpService();
    }

    public void unbindUpnpService (IClingBasedriver service) {
        this.upnpService = null;
    }

    /*
      * When an external device is discovered wrap it with UPnPDeviceImpl,
      * create a tracker for any listener to this device or its services,
      * and register the UPnPDevice.
      */
    @Override
    public void deviceAdded(Registry registry, @SuppressWarnings("rawtypes") Device device) {
        log.entering(this.getClass().getName(), "deviceAdded", new Object[]{registry, device});

        if (device instanceof RemoteDevice) {
            String string = String.format("(%s=%s)",
                    Constants.OBJECTCLASS, UPnPEventListener.class.getName()
            );
            try {
                synchronized (deviceList) {
                    final Dictionary<String, Device> props = new Hashtable<>();
                    props.put(UPnPDeviceImpl.UPNP_CLING_DEVICE, device);
                    instance = factory.newInstance(props);
                    UPnPDeviceImpl upnpDevice = (UPnPDeviceImpl) instance.getInstance();
                    deviceList.add(upnpDevice);
                }
            } catch (Exception e) {
                log.severe(String.format("Cannot add remote device (%s).", device.getIdentity().getUdn().toString()));
                log.severe(e.getMessage());
            }
        }
    }

    @Override
    public void deviceRemoved(Registry registry, @SuppressWarnings("rawtypes") Device device) {
        log.entering(this.getClass().getName(), "deviceRemoved", new Object[]{registry, device});
        instance.dispose();
    }

    @Reference(target = "(component.factory=upnpdevice.factory)")
    public void bindFactory(final ComponentFactory factory) {
        this.factory = factory;
    }

    public void unbindFactory(final ComponentFactory factory) {
        this.factory = null;
    }

    @Reference (
            service = UPnPEventListener.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE
    )
    public void bindUPnPEventListener(UPnPEventListener listener, Map<String, ?> props) {
        log.entering(this.getClass().getName(), "bindUPnPListener");
        synchronized (deviceList) {
            Filter filter = (Filter) props.get(UPnPEventListener.UPNP_FILTER);
            if (filter != null) {
                for (UPnPDeviceImpl device : deviceList) {
                    List<SubscriptionCallback> callbacks = new ArrayList<SubscriptionCallback>();
                    UPnPServiceImpl[] services = (UPnPServiceImpl[]) device.getServices();
                    if (services != null) {
                        Dictionary descriptions = device.getDescriptions(null);
                        boolean all = filter.match(descriptions);

                        if (all) {
                            log.finer(String.format(
                                    "Matched UPnPEvent listener for device %s service: ALL.",
                                    device.getDevice().getIdentity().getUdn().toString()
                            ));
                        }

                        for (UPnPServiceImpl service : services) {
                            boolean match = all;

                            if (!match) {
                                Dictionary dictionary = new Hashtable();
                                for (Object key : Collections.list(descriptions.keys())) {
                                    dictionary.put(key, descriptions.get(key));
                                }
                                dictionary.put(UPnPService.ID, service.getId());
                                dictionary.put(UPnPService.TYPE, service.getType());
                                match = filter.match(dictionary);
                                if (match) {
                                    log.finer(String.format(
                                            "Matched UPnPEvent listener for device %s service: %s.",
                                            device.getDevice().getIdentity().getUdn().toString(), service.getId()
                                    ));
                                }
                            }

                            if (match) {
                                log.finer(String.format(
                                        "Creating subscription callback for device %s service: %s.",
                                        device.getDevice().getIdentity().getUdn().toString(), service.getId()
                                ));
                                SubscriptionCallback callback = new UPnPEventListenerSubscriptionCallback(device, service, listener);
                                upnpService.getControlPoint().execute(callback);
                                callbacks.add(callback);
                            }
                        }
                    }

                    listenerCallbacks.put(listener, callbacks);
                }
            }
        }
    }

    public void unbindUPnPEventListener(UPnPEventListener listener) {
        log.entering(this.getClass().getName(), "removedService", new Object[]{ listener});

        List<SubscriptionCallback> callbacks = listenerCallbacks.get(listener);
        if (callbacks != null) {
            for (SubscriptionCallback callback : callbacks) {
                // TODO: callbacks are executed ... don't know how to remove them
            }
        }

        listenerCallbacks.remove(listener);
    }
}
