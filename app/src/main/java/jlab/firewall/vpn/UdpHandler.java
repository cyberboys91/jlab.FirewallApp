package jlab.firewall.vpn;

import android.net.VpnService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import static jlab.firewall.vpn.FirewallService.isRunning;

/**
 *
 */
public class UdpHandler implements Runnable {

    BlockingQueue<Packet> queue;

    BlockingQueue<ByteBuffer> networkToDeviceQueue;
    VpnService vpnService;

    private Selector selector;
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;

    private static class UdpDownWorker implements Runnable {

        BlockingQueue<ByteBuffer> networkToDeviceQueue;
        BlockingQueue<UdpTunnel> tunnelQueue;
        Selector selector;

        private static AtomicInteger ipId = new AtomicInteger();

        private void sendUdpPack(UdpTunnel tunnel, byte[] data) throws IOException {
            int dataLen = 0;
            if (data != null) {
                dataLen = data.length;
            }
            Packet packet = IpUtil.buildUdpPacket(tunnel.remote, tunnel.local, ipId.addAndGet(1));

            ByteBuffer byteBuffer = ByteBufferPool.acquire();
            //
            byteBuffer.position(HEADER_SIZE);
            if (data != null) {
                if (byteBuffer.remaining() < data.length) {
                    System.currentTimeMillis();
                }
                byteBuffer.put(data);
            }
            packet.updateUDPBuffer(byteBuffer, dataLen);
            byteBuffer.position(HEADER_SIZE + dataLen);
            this.networkToDeviceQueue.offer(byteBuffer);
        }


        public UdpDownWorker(Selector selector, BlockingQueue<ByteBuffer> networkToDeviceQueue, BlockingQueue<UdpTunnel> tunnelQueue) {
            this.networkToDeviceQueue = networkToDeviceQueue;
            this.tunnelQueue = tunnelQueue;
            this.selector = selector;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted() && isRunning()) {
                    int readyChannels = selector.select();
                    while (!Thread.interrupted() && isRunning()) {
                        UdpTunnel tunnel = tunnelQueue.poll();
                        if (tunnel == null) {
                            break;
                        } else {
                            try {
                                SelectionKey key = tunnel.channel.register(selector, SelectionKey.OP_READ, tunnel);
                                key.interestOps(SelectionKey.OP_READ);
                            } catch (IOException e) {
                                //TODO: disable log
                                //Log.d(TAG, "register fail", e);
                            }
                        }
                    }
                    if (readyChannels == 0) {
                        selector.selectedKeys().clear();
                        continue;
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        if (key.isValid() && key.isReadable()) {
                            try {
                                DatagramChannel inputChannel = (DatagramChannel) key.channel();
                                ByteBuffer receiveBuffer = ByteBufferPool.acquire();
                                inputChannel.read(receiveBuffer);
                                receiveBuffer.flip();
                                byte[] data = new byte[receiveBuffer.remaining()];
                                receiveBuffer.get(data);
                                UdpTunnel tunnel = (UdpTunnel) key.attachment();
                                sendUdpPack(tunnel, data);
                            } catch (IOException e) {
                                //TODO: disable log
                                //Log.e(TAG, "error", e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                //TODO: disable log
                //Log.e(TAG, "error", e);
                //TODO: jlab. Se crashea si se descomenta
                //System.exit(0);
            } finally {
                //TODO: disable log
                //Log.d(TAG, "UdpHandler quit");
            }
        }
    }

    public UdpHandler(BlockingQueue<Packet> queue, BlockingQueue<ByteBuffer> networkToDeviceQueue, VpnService vpnService) {
        this.queue = queue;
        this.networkToDeviceQueue = networkToDeviceQueue;
        this.vpnService = vpnService;
    }

    Map<String, DatagramChannel> udpSockets = new HashMap();

    private static class UdpTunnel {
        InetSocketAddress local;
        InetSocketAddress remote;
        DatagramChannel channel;
    }

    @Override
    public void run() {
        try {
            BlockingQueue<UdpTunnel> tunnelQueue = new ArrayBlockingQueue<>(100);
            selector = Selector.open();
            Thread t = new Thread(new UdpDownWorker(selector, networkToDeviceQueue, tunnelQueue));
            t.start();

            while (!Thread.interrupted() && isRunning()) {
                Packet packet = queue.take();

                InetAddress destinationAddress = packet.ip4Header.destinationAddress;
                Packet.UDPHeader header = packet.udpHeader;

                //Log.d(TAG, String.format("get pack %d udp %d ", packet.packId, header.length));

                int destinationPort = header.destinationPort;
                int sourcePort = header.sourcePort;
                String ipAndPort = new StringBuilder(destinationAddress.getHostAddress())
                        .append(":").append(destinationPort)
                        .append(":").append(sourcePort).toString();
                if (!udpSockets.containsKey(ipAndPort)) {
                    DatagramChannel outputChannel = DatagramChannel.open();
                    vpnService.protect(outputChannel.socket());
                    outputChannel.socket().bind(null);
                    outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));

                    outputChannel.configureBlocking(false);

                    UdpTunnel tunnel = new UdpTunnel();
                    tunnel.local = new InetSocketAddress(packet.ip4Header.sourceAddress, header.sourcePort);
                    tunnel.remote = new InetSocketAddress(packet.ip4Header.destinationAddress, header.destinationPort);
                    tunnel.channel = outputChannel;
                    tunnelQueue.offer(tunnel);

                    selector.wakeup();

                    udpSockets.put(ipAndPort, outputChannel);
                }

                DatagramChannel outputChannel = udpSockets.get(ipAndPort);
                ByteBuffer buffer = packet.backingBuffer;
                try {
                    while (packet.backingBuffer.hasRemaining())
                        outputChannel.write(buffer);
                } catch (IOException e) {
                    //TODO: disable log
                    //Log.e(TAG, "udp write error", e);
                    outputChannel.close();
                    udpSockets.remove(ipAndPort);
                    NetConnections.removeFromCache(ipAndPort);
                }
            }
        } catch (Exception e) {
            //TODO: disable log
            //Log.e(TAG, "error", e);
            //TODO: jlab. Se crashea
            //System.exit(0);
        }
    }
}
