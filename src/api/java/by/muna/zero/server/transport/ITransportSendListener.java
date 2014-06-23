package by.muna.zero.server.transport;

public interface ITransportSendListener {
    void onSent();
    void onCancelled();

    /**
     * @param bytesSent Количество байт, которые скорее всего ушли другой стороне.
     */
    void onFail(int bytesSent);
}
