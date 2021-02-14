package jlab.firewall.vpn;
/*
 * Created by Javier on 31/12/2020.
 */

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.BlockingQueue;
import static jlab.firewall.vpn.FirewallService.isRunning;

public class WriteVpnRunnable implements Runnable {

    private FileChannel vpnOutput;
    private BlockingQueue<ByteBuffer> networkToDeviceQueue;

    WriteVpnRunnable(FileChannel vpnOutput, BlockingQueue<ByteBuffer> networkToDeviceQueue) {
        this.vpnOutput = vpnOutput;
        this.networkToDeviceQueue = networkToDeviceQueue;
    }

    @Override
    public void run() {
        while (!Thread.interrupted() && isRunning()) {
            try {
                ByteBuffer bufferFromNetwork = networkToDeviceQueue.take();
                bufferFromNetwork.flip();

                while (bufferFromNetwork.hasRemaining())
                    vpnOutput.write(bufferFromNetwork);
            } catch (Exception e) {
                //TODO: disable log
                //e.printStackTrace();
            }
        }
    }
}
