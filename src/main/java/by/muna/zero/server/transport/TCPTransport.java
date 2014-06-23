package by.muna.zero.server.transport;

import by.muna.network.tcp.ITCPServer;
import by.muna.network.tcp.ITCPServerListener;
import by.muna.network.tcp.ITCPSocket;

public class TCPTransport implements ITransport {
    int maxPacketLength;

    private ITransportListener listener;

    public TCPTransport(ITCPServer server, int maxPacketLength) {
        this.maxPacketLength = maxPacketLength;

        server.setListener(new ITCPServerListener() {
            @Override
            public void onConnected(ITCPSocket socket) {
                new TCPTransportUser(TCPTransport.this, socket);
            }

            @Override
            public void onStop() {
                TCPTransport.this.listener.onStop();
            }
        });
    }

    @Override
    public void setListener(ITransportListener listener) {
        this.listener = listener;
    }

    void onPacket(ITransportPacket packet) {
        this.listener.onPacket(packet);
    }

    @Override
    public void stop() {
        throw new RuntimeException("Not implemented yet.");
    }
}
