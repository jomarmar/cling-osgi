package org.jemz.core.upnp;

import org.osgi.service.upnp.UPnPLocalStateVariable;
import org.osgi.service.upnp.UPnPStateVariable;

import java.util.Date;

/**
 * Created by jmartinez on 12/30/15.
 */
public class UPnPTypeUtil {

    public static Object getJavaType(String upnpType, String value) {
        if( upnpType.equals(UPnPStateVariable.TYPE_UI1) ||
            upnpType.equals(UPnPStateVariable.TYPE_UI2) ||
            upnpType.equals(UPnPStateVariable.TYPE_I1) ||
            upnpType.equals(UPnPStateVariable.TYPE_I2) ||
            upnpType.equals(UPnPStateVariable.TYPE_I4) ||
            upnpType.equals(UPnPStateVariable.TYPE_INT)
                ) {

            return Integer.parseInt(value);

        } else if( upnpType.equals(UPnPStateVariable.TYPE_UI4) ||
                   upnpType.equals(UPnPStateVariable.TYPE_TIME) ||
                   upnpType.equals(UPnPStateVariable.TYPE_TIME_TZ)
                    ) {
            return Long.parseLong(value);
        } else if( upnpType.equals(UPnPStateVariable.TYPE_R4) ||
                upnpType.equals(UPnPStateVariable.TYPE_FLOAT)
                ) {
            return Float.parseFloat(value);
        } else if( upnpType.equals(UPnPStateVariable.TYPE_R8) ||
                upnpType.equals(UPnPStateVariable.TYPE_NUMBER) ||
                upnpType.equals(UPnPStateVariable.TYPE_FIXED_14_4)
                ) {
            return Double.parseDouble(value);
        } else if( upnpType.equals(UPnPStateVariable.TYPE_CHAR)) {
            return new Character(value.charAt(0));
        } else if( upnpType.equals(UPnPStateVariable.TYPE_STRING) ||
                    upnpType.equals(UPnPStateVariable.TYPE_URI) ||
                    upnpType.equals(UPnPStateVariable.TYPE_UUID)) {
            return value;
        } else if( upnpType.equals(UPnPStateVariable.TYPE_BOOLEAN)) {
            return Boolean.parseBoolean(value);
        } else if( upnpType.equals(UPnPStateVariable.TYPE_BIN_BASE64) ||
                   upnpType.equals(UPnPStateVariable.TYPE_BIN_HEX)) {
            return new Byte[0]; //TODO
        } else {

            return null;
        }


    }
}
