package jlab.firewall.vpn;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ByteBufferPool
{
    private static final int BUFFER_SIZE = 16384; // XXX: Is this ideal?

    public static ByteBuffer acquire() {
        return ByteBuffer.allocate(BUFFER_SIZE);
    }
}
