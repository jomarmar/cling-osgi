package org.jemz.core.upnp.subscriber.internal;

import org.fourthline.cling.binding.annotations.UpnpAction;
import org.osgi.framework.BundleContext;
import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPEventListener;
import org.osgi.service.upnp.UPnPService;

import java.util.Dictionary;
import java.util.Locale;

/**
 * Created by jmartinez on 3/1/14.
 */
public class JFUPnPControl implements UPnPEventListener {
    private JFUPnPSubscriber subscriber;

    public void setBundleContext(BundleContext context) {

        System.out.println("SUBSCRIBE TO EVENTS");
        subscriber = new JFUPnPSubscriber(context, this);

        subscriber.subscribeEveryDeviceTypeServices("urn:schemas-upnp-org:device:BinaryLight:1");
        subscriber.subscribeEveryDeviceTypeServices("urn:schemas-4thline-com:device:simple-test:1");
    }

    public void startup() {
        System.out.println("STARTING UP UPNPCONTROL");

    }

    public void shutdown() {
        System.out.println("SHUTTING DOWN UPNPCONTROL");
        subscriber.unsubscribeAll();
    }

    public void registerUPnPDevice(UPnPDevice device) {
        System.out.println("REGISTERING UPNPDEVICE: " + device.getDescriptions(String.valueOf(Locale.getDefault())));
        for(UPnPService service : device.getServices()) {
            System.out.println("SERVICE: " + service.getId() + " TYPE: " + service.getType());
            for(UPnPAction action : service.getActions()) {
                System.out.println(" ACTION: " + action.getName());
            }
        }

    }


    public void deregisterUPnPDevice(UPnPDevice device) {
        if(device != null) {
            System.out.println("DEREGISTERING UPNPDEVICE: " + device.getDescriptions(String.valueOf(Locale.getDefault())));
        }
    }

    @Override
    public void notifyUPnPEvent(String deviceId, String serviceId, Dictionary events) {
        System.out.println("[EVENT] DeviceID: " + deviceId);
        System.out.println("[EVENT] ServiceID: " + serviceId);
        System.out.println("[EVENT] Event: " + events.toString());

    }
}
