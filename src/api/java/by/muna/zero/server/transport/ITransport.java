package by.muna.zero.server.transport;

public interface ITransport {
    void setListener(ITransportListener listener);

    void stop();
}
