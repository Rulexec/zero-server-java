package by.muna.zero.server.transport;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public interface ITransportSendRequest {
    Supplier<ByteBuffer> getBufferSupplier();
    ITransportSendListener getSendListener();
}
