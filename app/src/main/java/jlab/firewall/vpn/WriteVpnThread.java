package jlab.firewall.vpn;
/*
 * Created by Javier on 31/12/2020.
 */

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.BlockingQueue;
import static jlab.firewall.vpn.FirewallService.downByteSpeed;
import static jlab.firewall.vpn.FirewallService.downByteTotal;
import static jlab.firewall.vpn.FirewallService.isRunning;

public class WriteVpnThread implements Runnable {

    private FileChannel vpnOutput;
    private BlockingQueue<ByteBuffer> networkToDeviceQueue;

    WriteVpnThread(FileChannel vpnOutput, BlockingQueue<ByteBuffer> networkToDeviceQueue) {
        this.vpnOutput = vpnOutput;
        this.networkToDeviceQueue = networkToDeviceQueue;
    }

    @Override
    public void run() {
        while (isRunning()) {
            try {
                ByteBuffer bufferFromNetwork = networkToDeviceQueue.take();
                bufferFromNetwork.flip();

                while (bufferFromNetwork.hasRemaining())
                    vpnOutput.write(bufferFromNetwork);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
