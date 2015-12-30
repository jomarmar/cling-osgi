package org.jemz.core.upnp;

import org.osgi.service.upnp.UPnPDevice;

import java.util.List;

/**
 * Created by jmartinez on 12/8/15.
 */
public interface IUPnPControl {
    List<UPnPDevice> getDeviceList();

    void enableEvents (boolean enable, String deviceUDN);
}
