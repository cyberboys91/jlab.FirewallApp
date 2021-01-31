package jlab.firewall.vpn;
/*
 * Created by Javier on 26/12/2020.
 */

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static android.content.Context.CONNECTIVITY_SERVICE;

public class NetConnections {

    private static final String zeroHostV4 = "00000000";
    private static final int TCP_PROTOCOL_INT = 6, UDP_PROTOCOL_INT = 17;
    public static final int LENGTH_PREFIX_HEX_ADDRESS_VERSION_6 = 24;
    private static ConnectivityManager connectivityManager;
    private static final LruCache<String, Integer> cache = new LruCache<>(100);

    public static void removeFromCache (String connection) {
        cache.remove(connection);
    }

    public static void freeCache () {
        cache.evictAll();
    }

    public static int getUid(Context context, Protocol protocol, InetAddress localHost, int localPort,
                             InetAddress remoteHost, int remotePort) {

        String connection = new StringBuilder(remoteHost.getHostAddress()).append(":")
                .append(remotePort).append(":").append(localPort).toString();

        Integer uid = cache.get(connection);
        if(uid != null)
            return uid;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uid = getUidQ(context, getProtocolInt(protocol), localHost,
                    localPort, remoteHost, remotePort);
            if (uid != -1)
                cache.put(connection, uid);
            return uid;
        }

        try {
            String line, localPortStr = completeLength(Integer.toHexString(localPort).toUpperCase(), 4),
                    remotePortStr = completeLength(Integer.toHexString(remotePort).toUpperCase(), 4);
            String localHostStr = getIpHexadecimal(localHost.getAddress()).toUpperCase(),
                    remoteHostStr = getIpHexadecimal(remoteHost.getAddress()).toUpperCase();

            //Search in v6
            BufferedReader reader = new BufferedReader(new FileReader(String.format("/proc/net/%s6", protocol)));
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (isConnection(true, line, localHostStr, remoteHostStr, localPortStr, remotePortStr)) {
                    String[] split = line.split("\\s+");
                    reader.close();
                    uid = Integer.parseInt(split[7]);
                    if (uid != -1)
                        cache.put(connection, uid);
                    return uid;
                }
            }
            reader.close();

            //Search in v4
            reader = new BufferedReader(new FileReader(String.format("/proc/net/%s", protocol)));
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (isConnection(false, line, localHostStr, remoteHostStr, localPortStr, remotePortStr)) {
                    String[] split = line.split("\\s+");
                    reader.close();
                    uid = Integer.parseInt(split[7]);
                    if (uid != -1)
                        cache.put(connection, uid);
                    return uid;
                }
            }
            reader.close();
        } catch (IOException e) {
            //TODO: disable log
            //e.printStackTrace();
        }
        return -1;
    }

    private static int getProtocolInt (Protocol protocol) {
        switch (protocol) {
            case tcp:
                return TCP_PROTOCOL_INT;
            case udp:
                return UDP_PROTOCOL_INT;
            default:
                return -1;
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private static int getUidQ(Context context, int protocol, InetAddress saddr, int sport,
                               InetAddress daddr, int dport) {
        if (protocol != 6 /* TCP */ && protocol != 17 /* UDP */)
            return -1;

        if (connectivityManager == null)
            connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null)
            return -1;

        InetSocketAddress local = new InetSocketAddress(saddr, sport);
        InetSocketAddress remote = new InetSocketAddress(daddr, dport);
        return connectivityManager.getConnectionOwnerUid(protocol, local, remote);
    }

    private static String completeLength(String src, int length) {
        int count = length - src.length();
        StringBuilder builder = new StringBuilder();
        while (count-- > 0)
            builder.append('0');
        builder.append(src);
        return builder.toString();
    }

    private static boolean isConnection(boolean isV6, String connection, String localHost, String remoteHost,
                                        String localPort, String remotePort) {
        HexDataPacket hexDataPacket = new HexDataPacket(isV6, connection);

        //Comparacion de netguard
        /*if (_sport == sport &&
                (_dport == dport || _dport == 0) &&
                (memcmp(_saddr, saddr, (size_t) (ws * 4)) == 0 ||
        memcmp(_saddr, zero, (size_t) (ws * 4)) == 0) &&
                (memcmp(_daddr, daddr, (size_t) (ws * 4)) == 0 ||
        memcmp(_daddr, zero, (size_t) (ws * 4)) == 0))*/

        return hexDataPacket.getLocalPort().equals(localPort) &&
                (hexDataPacket.getRemotePort().equals(remotePort) || Integer.parseInt(hexDataPacket.getRemotePort()) == 0) &&
                (hexDataPacket.getLocalHost().equals(localHost) ||
                        hexDataPacket.getLocalHost().equals(zeroHostV4)) &&
                (hexDataPacket.getRemoteHost().equals(remoteHost) ||
                        hexDataPacket.getRemoteHost().equals(zeroHostV4));
    }

    public static String getIpHexadecimal(byte[] ip) {
        StringBuilder result = new StringBuilder();
        for (int i = ip.length - 1; i >= 0; i--)
            result.append(completeLength(Integer.toHexString(ip[i] & 0xff), 2));
        return result.toString();
    }

    public enum Protocol {
        tcp,
        udp
    }
}