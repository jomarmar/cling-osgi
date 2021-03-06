package org.fourthline.cling.osgi.basedriver;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.message.header.STAllHeader;
import org.fourthline.cling.model.meta.*;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;

import java.util.Collection;
import java.util.List;

/**
 * Created by jmartinez on 11/15/15.
 */
public class Main {
    public static void main(String[] args) throws Exception {

        TestSubscriptionCallback cb = null;

        // UPnP discovery is asynchronous, we need a callback
        RegistryListener listener = new RegistryListener() {

            public void remoteDeviceDiscoveryStarted(Registry registry,
                                                     RemoteDevice device) {
                System.out.println(
                        "Discovery started: " + device.getDisplayString()
                );
            }

            public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
                System.out.println(
                        "Discovery failed: " + device.getDisplayString() + " => " + ex
                );
            }

            public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
                System.out.println(
                        "Remote device available: " + device.getDisplayString()
                );

            }

            public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
                System.out.println(
                        "Remote device updated: " + device.getDisplayString()
                );
            }

            public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
                System.out.println(
                        "Remote device removed: " + device.getDisplayString()
                );
            }

            public void localDeviceAdded(Registry registry, LocalDevice device) {
                System.out.println(
                        "Local device added: " + device.getDisplayString()
                );


            }

            public void localDeviceRemoved(Registry registry, LocalDevice device) {
                System.out.println(
                        "Local device removed: " + device.getDisplayString()
                );
            }

            public void beforeShutdown(Registry registry) {
                System.out.println(
                        "Before shutdown, the registry has devices: " + registry.getDevices().size()
                );
                for(Device dev : registry.getDevices()) {
                    System.out.println("DEV: " + dev.getDisplayString());
                }
            }

            public void afterShutdown() {
                System.out.println("Shutdown of registry complete!");

            }
        };

        // This will create necessary network resources for UPnP right away
        System.out.println("Starting Cling...");
        //UpnpService upnpService = new UpnpServiceImpl(listener);
        UpnpService upnpService = new UpnpServiceImpl(new ApacheUpnpServiceConfiguration());

        upnpService.getControlPoint().getRegistry().addListener(listener);
        // Send a search message to all devices and services, they should respond soon
        System.out.println("Sending SEARCH message to all devices...");
        upnpService.getControlPoint().search(new STAllHeader());

        // Let's wait 10 seconds for them to respond
        System.out.println("Waiting 10 seconds before shutting down...");
        Thread.sleep(10000);

        Collection<Device> devices = upnpService.getControlPoint().getRegistry().getDevices();
        for(Device device : devices) {
            if(device.getType().toString().equals("urn:schemas-upnp-org:device:BinaryLight:1")) {
                for(Service service : device.getServices()) {
                    cb = new TestSubscriptionCallback( service);
                    upnpService.getControlPoint().execute(cb);
                }
            }

        }


        Thread.sleep(10000);

        // Release all resources and advertise BYEBYE to other UPnP devices
        System.out.println("Stopping Cling...");
        upnpService.shutdown();
    }
}
