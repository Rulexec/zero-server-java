package by.muna.zero.server.transport;

public interface ITransportListener {
    void onPacket(ITransportPacket packet);

    /**
     * Вызывается если транспорт по каким-то причинам закончил свою работу.
     */
    void onStop();
}
