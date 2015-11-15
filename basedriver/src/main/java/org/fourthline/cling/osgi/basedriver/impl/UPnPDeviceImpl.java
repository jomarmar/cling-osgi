package org.fourthline.cling.osgi.basedriver.impl;

import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.meta.*;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPEventListener;
import org.osgi.service.upnp.UPnPIcon;
import org.osgi.service.upnp.UPnPService;

import java.util.*;
import java.util.logging.Logger;

/**
 * Created by jmartinez on 11/11/15.
 */
@Component (
        service = UPnPDevice.class,
        factory = "upnpdevice.factory"
)
public class UPnPDeviceImpl implements UPnPDevice {

    public static final String UPNP_CLING_DEVICE = "upnp.cling.device";

    private static final Logger log = Logger.getLogger(UPnPDeviceImpl.class.getName());

    private Device<?, ?, ?> device;
    private UPnPServiceImpl[] services;
    private Hashtable<String, UPnPService> servicesIndex;
    private UPnPIconImpl[] icons;
    private Dictionary<String, Object> descriptions = new Hashtable<String, Object>();
    private Map<UPnPEventListener, List<SubscriptionCallback>> listenerCallbacks = new Hashtable();




    @Activate
    public void activate(final Map<String, ?> properties) {
        Device<?, ?, ?> dev = (Device<?, ?, ?>) properties.get(UPNP_CLING_DEVICE);
        if(dev != null) {
            //this.upnpDevice = new UPnPDeviceImpl(dev);
            this.device = dev;
        }

        DeviceDetails deviceDetails = device.getDetails();
        ManufacturerDetails manufacturerDetails = deviceDetails.getManufacturerDetails();
        ModelDetails modelDetails = deviceDetails.getModelDetails();

		/* DEVICE CATEGORY */
        descriptions.put(
                org.osgi.service.device.Constants.DEVICE_CATEGORY,
                new String[]{ UPnPDevice.DEVICE_CATEGORY }
        );

        // mandatory properties
        if (!device.isRoot()) {
            Device<?, ?, ?> parent = device.getParentDevice();
            descriptions.put(UPnPDevice.PARENT_UDN, parent.getIdentity().getUdn().toString());
        }

        if (device.getEmbeddedDevices() != null) {
            List<String> list = new ArrayList<String>();

            for (Device<?, ?, ?> embedded : device.getEmbeddedDevices()) {
                list.add(embedded.getIdentity().getUdn().toString());
            }

            descriptions.put(UPnPDevice.CHILDREN_UDN, list.toArray(new String[list.size()]));
        }

        descriptions.put(UPnPDevice.FRIENDLY_NAME, deviceDetails.getFriendlyName());
        descriptions.put(UPnPDevice.MANUFACTURER, manufacturerDetails.getManufacturer());
        descriptions.put(UPnPDevice.TYPE, device.getType().toString());
        descriptions.put(UPnPDevice.UDN, device.getIdentity().getUdn().toString());

        // optional properties (but recommended)
        if (modelDetails.getModelDescription() != null) {
            descriptions.put(UPnPDevice.MODEL_DESCRIPTION, modelDetails.getModelDescription());
        }
        if (modelDetails.getModelNumber() != null) {
            descriptions.put(UPnPDevice.MODEL_NUMBER, modelDetails.getModelNumber());
        }
        if (deviceDetails.getPresentationURI() != null) {
            descriptions.put(UPnPDevice.PRESENTATION_URL, deviceDetails.getPresentationURI().toString());
        }
        if (deviceDetails.getSerialNumber() != null) {
            descriptions.put(UPnPDevice.SERIAL_NUMBER, deviceDetails.getSerialNumber());
        }

        // optional properties
        if (manufacturerDetails.getManufacturerURI() != null) {
            descriptions.put(UPnPDevice.MANUFACTURER_URL, manufacturerDetails.getManufacturerURI().toString());
        }
        if (modelDetails.getModelName() != null) {
            descriptions.put(UPnPDevice.MODEL_NAME, modelDetails.getModelName());
        }
        if (modelDetails.getModelURI() != null) {
            descriptions.put(UPnPDevice.MODEL_URL, modelDetails.getModelURI().toString());
        }
        if (deviceDetails.getUpc() != null) {
            descriptions.put(UPnPDevice.UPC, deviceDetails.getUpc());
        }

        if (device.getServices() != null && device.getServices().length != 0) {
            List<UPnPServiceImpl> list = new ArrayList<UPnPServiceImpl>();
            servicesIndex = new Hashtable<String, UPnPService>();

            for (Service<?, ?> service : device.getServices()) {
                UPnPServiceImpl item = new UPnPServiceImpl(service);
                list.add(item);
                servicesIndex.put(item.getId(), item);
            }

            services = list.toArray(new UPnPServiceImpl[list.size()]);
        }

        if (device.getIcons() != null && device.getIcons().length != 0) {
            List<UPnPIconImpl> list = new ArrayList<UPnPIconImpl>();

            for (Icon icon : device.getIcons()) {
                UPnPIconImpl item = new UPnPIconImpl(icon);
                list.add(item);
            }

            icons = list.toArray(new UPnPIconImpl[list.size()]);
        }
    }


//    public UPnPDevice getDevice() {
//        return upnpDevice;
//    }



    @Override
    public UPnPService getService(String serviceId) {
        return (servicesIndex != null) ? servicesIndex.get(serviceId) : null;
    }


    @Override
    public UPnPService[] getServices() {
        return services;
    }

    @Override
    public UPnPIcon[] getIcons(String locale) {
        return icons;
    }

    @Override
    public Dictionary<String, Object> getDescriptions(String locale) {
        return descriptions;
    }

    public Device<?, ?, ?> getDevice() {
        return device;
    }
//
//
//    @Reference(
//            service = UPnPEventListener.class,
//            cardinality = ReferenceCardinality.MULTIPLE
//    )
//    public void bindUPnPEventListener(UPnPEventListener listener, Map<String, ?> props) {
//        log.entering(this.getClass().getName(), "bindUPnPListener");
//
//        Filter filter = (Filter) props.get(UPnPEventListener.UPNP_FILTER);
//        if (filter != null) {
//            List<SubscriptionCallback> callbacks = new ArrayList<SubscriptionCallback>();
//            UPnPServiceImpl[] services = (UPnPServiceImpl[]) getServices();
//            if (services != null) {
//                Dictionary descriptions = getDescriptions(null);
//                boolean all = filter.match(descriptions);
//
//                if (all) {
//                    log.finer(String.format(
//                            "Matched UPnPEvent listener for device %s service: ALL.",
//                            getDevice().getIdentity().getUdn().toString()
//                    ));
//                }
//
//                for (UPnPServiceImpl service : services) {
//                    boolean match = all;
//
//                    if (!match) {
//                        Dictionary dictionary = new Hashtable();
//                        for (Object key : Collections.list(descriptions.keys())) {
//                            dictionary.put(key, descriptions.get(key));
//                        }
//                        dictionary.put(UPnPService.ID, service.getId());
//                        dictionary.put(UPnPService.TYPE, service.getType());
//                        match = filter.match(dictionary);
//                        if (match) {
//                            log.finer(String.format(
//                                    "Matched UPnPEvent listener for device %s service: %s.",
//                                    getDevice().getIdentity().getUdn().toString(), service.getId()
//                            ));
//                        }
//                    }
//
//                    if (match) {
//                        log.finer(String.format(
//                                "Creating subscription callback for device %s service: %s.",
//                                getDevice().getIdentity().getUdn().toString(), service.getId()
//                        ));
////                        SubscriptionCallback callback = new UPnPEventListenerSubscriptionCallback(this, service, listener);
////                        getControlPoint().execute(callback);
////                        callbacks.add(callback);
//                    }
//                }
//            }
//
//            listenerCallbacks.put(listener, callbacks);
//        }
//    }
//
//    public void unbindUPnPEventListener(UPnPEventListener listener) {
//        log.entering(this.getClass().getName(), "removedService", new Object[]{ listener});
//
//        List<SubscriptionCallback> callbacks = listenerCallbacks.get(listener);
//        if (callbacks != null) {
//            for (SubscriptionCallback callback : callbacks) {
//                // TODO: callbacks are executed ... don't know how to remove them
//            }
//        }
//
//        listenerCallbacks.remove(listener);
//    }

}
