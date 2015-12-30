package org.jemz.core.upnp.internal;

import org.jemz.core.upnp.IUPnPControl;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Locale;

/**
 * Created by jmartinez on 3/1/14.
 */
@Component (
        service = {UPnPEventListener.class, IUPnPControl.class},
        immediate = true
)
public class JFUPnPControl implements UPnPEventListener, IUPnPControl {
    private static final Logger log = LoggerFactory.getLogger(JFUPnPControl.class);

    private JFUPnPSubscriber subscriber;
    private List<UPnPDevice> deviceList = new ArrayList<>();


//    public void setBundleContext(BundleContext context) {
//
//
//    }

    @Activate
    public void startup(BundleContext context) {
        log.info("STARTING UP UPNPCONTROL");

        subscriber = new JFUPnPSubscriber(context, this);
    }

    @Deactivate
    public void shutdown() {
        log.info("SHUTTING DOWN UPNPCONTROL");
        subscriber.unsubscribeAll();
    }

    @Reference (
            name = "upnpDevice", service = UPnPDevice.class,
            cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC
    )
    public void bindUPnPDevice(UPnPDevice device) {
        log.info("REGISTER DEVICE: " + device.getDescriptions(null).get(UPnPDevice.FRIENDLY_NAME) + " MODEL: " + device.getDescriptions(null).get(UPnPDevice.MODEL_NAME) + " DESCRIPTION: " + device.getDescriptions(null).get(UPnPDevice.MODEL_DESCRIPTION));
        deviceList.add(device);
//        String deviceType = (String) device.getDescriptions(null).get("UPnP.device.type");
//        if(deviceType.equals("urn:schemas-upnp-org:device:BinaryLight:1")) {
//            subscriber.subscribeEveryDeviceTypeServices(deviceType);
//        } else if (deviceType.equals("urn:schemas-4thline-com:device:simple-test:1")) {
//            subscriber.subscribeEveryDeviceTypeServices(deviceType);
//        }
    }

    public void unbindUPnPDevice(UPnPDevice device) {
        if(device != null) {
            log.info("DEREGISTERING UPNPDEVICE: " + device.getDescriptions(String.valueOf(Locale.getDefault())));
            deviceList.remove(device);
        }
    }

    public List<UPnPDevice> getDeviceList() {
        return deviceList;
    }

    @Override
    public void enableEvents (boolean enable, String deviceUDN) {
        if(enable) {
            subscriber.subscribeAllServicesOf(deviceUDN);
        } else {
            subscriber.unsubscribeAllServicesOf(deviceUDN);
        }
    }

    @Override
    public void notifyUPnPEvent(String deviceId, String serviceId, Dictionary events) {
        System.out.println("[EVENT] DeviceID: " + deviceId);
        System.out.println("[EVENT] ServiceID: " + serviceId);
        System.out.println("[EVENT] Event: " + events.toString());

    }
}
