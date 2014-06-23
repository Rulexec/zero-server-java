package by.muna.zero.server.transport;

import java.nio.ByteBuffer;

public class TransportPacket implements ITransportPacket {
    private ITransportUser user;
    private ByteBuffer buffer;
    private boolean valid;

    public TransportPacket(ITransportUser user, ByteBuffer buffer, boolean valid) {
        this.user = user;
        this.buffer = buffer;
        this.valid = valid;
    }

    @Override
    public ITransportUser getUser() {
        return this.user;
    }

    @Override
    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    @Override
    public boolean isValid() {
        return this.valid;
    }
}
