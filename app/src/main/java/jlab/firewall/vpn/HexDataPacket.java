package jlab.firewall.vpn;
/*
 * Created by Javier on 31/12/2020.
 */

import static jlab.firewall.vpn.NetConnections.LENGTH_PREFIX_HEX_ADDRESS_VERSION_6;

class HexDataPacket {
    private String localHost;
    private String remoteHost;
    private String localPort;
    private String remotePort;

    public HexDataPacket(String localConn, String remoteConn) {
        String [] split = localConn.split(":");
        this.localHost = split[0];
        this.localPort = split[1];
        split = remoteConn.split(":");
        this.remoteHost = split[0];
        this.remotePort = split[1];
    }

    public HexDataPacket(boolean isV6, String completeTrace) {
        int startIndex;
        for (int i = 0; i < completeTrace.length(); i++)
            if (completeTrace.charAt(i) == ' ') {
                i += (isV6 ? LENGTH_PREFIX_HEX_ADDRESS_VERSION_6 : 0) + 1;
                startIndex = i;
                while (completeTrace.charAt(i) != ':') i++;
                localHost = completeTrace.substring(startIndex, i);
                startIndex = i + 1;
                while (completeTrace.charAt(i) != ' ') i++;
                localPort = completeTrace.substring(startIndex, i);
                i += (isV6 ? LENGTH_PREFIX_HEX_ADDRESS_VERSION_6 : 0) + 1;
                startIndex = i;
                while (completeTrace.charAt(i) != ':') i++;
                remoteHost = completeTrace.substring(startIndex, i);
                startIndex = i + 1;
                while (completeTrace.charAt(i) != ' ') i++;
                remotePort = completeTrace.substring(startIndex, i);
                break;
            }
    }

    public String getLocalPort() {
        return localPort;
    }

    public String getRemotePort() {
        return remotePort;
    }

    public String getLocalHost() {
        return localHost;
    }

    public String getRemoteHost() {
        return remoteHost;
    }
}
