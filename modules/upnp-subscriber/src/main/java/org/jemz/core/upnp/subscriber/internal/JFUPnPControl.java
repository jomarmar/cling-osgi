package org.jemz.core.upnp.subscriber.internal;

import org.fourthline.cling.binding.annotations.UpnpAction;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPEventListener;
import org.osgi.service.upnp.UPnPService;

import java.util.Dictionary;
import java.util.Locale;

/**
 * Created by jmartinez on 3/1/14.
 */
@Component (
        immediate = true
)
public class JFUPnPControl implements UPnPEventListener {
    private JFUPnPSubscriber subscriber;

    public void setBundleContext(BundleContext context) {


    }

    @Activate
    public void startup(BundleContext context) {
        System.out.println("STARTING UP UPNPCONTROL");
        System.out.println("SUBSCRIBE TO EVENTS");
        subscriber = new JFUPnPSubscriber(context, this);

//        subscriber.subscribeEveryDeviceTypeServices("urn:schemas-upnp-org:device:BinaryLight:1");
//        subscriber.subscribeEveryDeviceTypeServices("urn:schemas-4thline-com:device:simple-test:1");

    }

    @Deactivate
    public void shutdown() {
        System.out.println("SHUTTING DOWN UPNPCONTROL");
        subscriber.unsubscribeAll();
    }

    @Reference (
            name = "upnpDevice", service = UPnPDevice.class,
            cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC
    )
    public void bindUPnPDevice(UPnPDevice device) {
        System.out.println("REGISTER DEVICE: " + device.getDescriptions(null).get(UPnPDevice.FRIENDLY_NAME) + " MODEL: " + device.getDescriptions(null).get(UPnPDevice.MODEL_NAME) + " DESCRIPTION: " + device.getDescriptions(null).get(UPnPDevice.MODEL_DESCRIPTION));

//        System.out.println("REGISTERING UPNPDEVICE: " + device.getDescriptions(String.valueOf(Locale.getDefault())));
//        for(UPnPService service : device.getServices()) {
//            System.out.println("SERVICE: " + service.getId() + " TYPE: " + service.getType());
//            for(UPnPAction action : service.getActions()) {
//                System.out.println(" ACTION: " + action.getName());
//            }
//        }


        String deviceType = (String) device.getDescriptions(null).get("UPnP.device.type");
        if(deviceType.equals("urn:schemas-upnp-org:device:BinaryLight:1")) {
            subscriber.subscribeEveryDeviceTypeServices(deviceType);
        } else if (deviceType.equals("urn:schemas-4thline-com:device:simple-test:1")) {
            subscriber.subscribeEveryDeviceTypeServices(deviceType);
        }




    }

    public void unbindUPnPDevice(UPnPDevice device) {
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
