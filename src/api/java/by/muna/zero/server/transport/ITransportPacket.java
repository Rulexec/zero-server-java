package by.muna.zero.server.transport;

import java.nio.ByteBuffer;

public interface ITransportPacket {
    ITransportUser getUser();

    ByteBuffer getBuffer();

    /**
     * @return Если false — значит пакет «испорченый» (получен не полностью/не сходится чексумма).
     */
    boolean isValid();
}
