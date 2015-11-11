package org.fourthline.cling.osgi.basedriver.discover;

import org.fourthline.cling.model.meta.*;
import org.fourthline.cling.osgi.basedriver.impl.UPnPIconImpl;
import org.fourthline.cling.osgi.basedriver.impl.UPnPServiceImpl;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPIcon;
import org.osgi.service.upnp.UPnPService;

import java.util.*;

/**
 * Created by jmartinez on 11/11/15.
 */
@Component (
        factory = "upnpdevice.factory"
)
public class UPnPDeviceFactory implements UPnPDevice {

    protected static final String UPNP_CLING_DEVICE = "upnp.cling.device";

    private Device<?, ?, ?> device;
    private UPnPServiceImpl[] services;
    private Hashtable<String, UPnPService> servicesIndex;
    private UPnPIconImpl[] icons;
    private Dictionary<String, Object> descriptions = new Hashtable<String, Object>();


    @Activate
    public void activate(final Map<String, ?> properties) {
        Device<?, ?, ?> dev = (Device<?, ?, ?>) properties.get(UPNP_CLING_DEVICE);
        if(dev != null) {
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

}
