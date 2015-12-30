package org.jemz.core.upnp.internal;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPEventListener;
import org.osgi.service.upnp.UPnPService;

import java.util.*;

public class JFUPnPSubscriber {
    private BundleContext context;
    private UPnPEventListener listener;
    private HashMap hash;

    private class Subscription implements UPnPEventListener {
        ServiceRegistration registration;

        Subscription(String keys){
            try {
                Filter filter = context.createFilter(keys);
                Properties props = new Properties();
                props.put(UPnPEventListener.UPNP_FILTER, filter);
                registration = context.registerService(UPnPEventListener.class.getName(), this, (Dictionary)props);
            }catch (Exception ex){
                System.out.println(ex);
            }
        }

        public void unsubscribe(){
            registration.unregister();
        }

        public void notifyUPnPEvent(String arg0, String arg1, Dictionary arg2) {
            listener.notifyUPnPEvent( arg0,  arg1,  arg2);
        }
    }


    public JFUPnPSubscriber(BundleContext context,UPnPEventListener listener){
        if ((context == null)|| (listener == null))
            throw new IllegalArgumentException("Illegal arguments in UPnPSubscriber constructor.");
        this.context = context;
        this.listener = listener;
        hash = new HashMap();
    }

    public void subscribe(String filter){
        if (hash.get(filter) == null){
            hash.put(filter, new Subscription(filter));
        }
    }

    public void unsubscribe(String filter){
        if (hash.containsKey(filter)) {
            Subscription subscription = (Subscription) hash.get(filter);
            subscription.unsubscribe();
            hash.remove(filter);
        }
    }

    public void unsubscribeAll(){
        Iterator list = hash.entrySet().iterator();
        while (list.hasNext()){
            Map.Entry entry = (Map.Entry) list.next();
            Subscription subcription = (Subscription) entry.getValue();
            subcription.unsubscribe();
            list.remove();
        }
    }

    public String subscribeServiceIdOf(String deviceId, String serviceId){
        String keys = "(&(" + UPnPDevice.ID + "="+ deviceId + ")(" + UPnPService.ID + "=" + serviceId + "))";
        subscribe(keys);
        return keys;
    }

    public String subscribeServiceTypeOf(String deviceId, String serviceType){
        String keys = "(&(" + UPnPDevice.ID + "="+ deviceId + ")(" + UPnPService.TYPE + "=" + serviceType + "))";
        subscribe(keys);
        return keys;
    }

    public String subscribeEveryServiceType(String deviceType, String serviceType){
        String keys = "(&(" + UPnPDevice.TYPE + "="+ deviceType + ")(" + UPnPService.TYPE + "=" + serviceType + "))";
        subscribe(keys);
        return keys;
    }

    public String subscribeAllServicesOf(String deviceId){
        String keys = "(" + UPnPDevice.ID + "="+ deviceId + ")";
        subscribe(keys);
        return keys;
    }

    public String subscribeEveryDeviceTypeServices(String deviceType){
        String keys = "(" + UPnPDevice.TYPE + "="+ deviceType + ")";
        subscribe(keys);
        return keys;
    }


    public void unsubscribeServiceIdOf(String deviceId, String serviceId){
        String keys = "(&(" + UPnPDevice.ID + "="+ deviceId + ")(" + UPnPService.ID + "=" + serviceId + "))";
        unsubscribe(keys);
    }

    public void unsubscribeAllServicesOf(String deviceId){
        String keys = "(" + UPnPDevice.ID + "="+ deviceId + ")";
        unsubscribe(keys);
    }
}