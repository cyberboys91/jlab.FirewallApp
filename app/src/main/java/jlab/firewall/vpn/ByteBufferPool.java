package jlab.firewall.vpn;

import java.nio.ByteBuffer;

public class ByteBufferPool
{
    private static final int BUFFER_SIZE = 65535; // XXX: Is this ideal?

    public static ByteBuffer acquire() {
        return ByteBuffer.allocate(BUFFER_SIZE);
    }
}
