package org.jemz.core.upnp.commands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jemz.core.upnp.IUPnPControl;
import org.osgi.service.component.annotations.*;
import org.osgi.service.upnp.UPnPDevice;

/**
 * Created by jmartinez on 12/8/15.
 */
@Command(scope = "upnp", name = "list-devices", description="Lists UPnP devices")
@Service
@Component(
        immediate = true
)
public class UPnPList implements Action {

    private static IUPnPControl upnpControl;

    @Activate
    public void startup() {

    }

    @Deactivate
    public void shutdown() {

    }


    @Override
    public Object execute() throws Exception {
        ShellTable table = new ShellTable();
        table.column("UDN").alignRight();
        table.column("NAME");
        //table.column("MODEL").alignRight();
        table.column("MODEL");
        table.column("TYPE");

        for(UPnPDevice dev : upnpControl.getDeviceList()) {
            table.addRow().addContent(  dev.getDescriptions(null).get(UPnPDevice.UDN),
                                        dev.getDescriptions(null).get(UPnPDevice.FRIENDLY_NAME),
                                        dev.getDescriptions(null).get(UPnPDevice.MODEL_NAME),
                                        dev.getDescriptions(null).get(UPnPDevice.TYPE));
        }


        table.print(System.out);

        return null;
    }

    @Reference( service = IUPnPControl.class,
                policy = ReferencePolicy.DYNAMIC,
                cardinality = ReferenceCardinality.MANDATORY)
    public void bindUPnPControl(IUPnPControl ctrl) {
        upnpControl = ctrl;
    }

    public void unbindUPnPControl(IUPnPControl ctrl) {
        upnpControl = null;
    }
}
