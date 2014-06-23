package by.muna.zero.server.transport.tests;

import by.muna.buffers.IBufferReadable;
import by.muna.buffers.util.BufferReadableUtil;
import by.muna.network.tcp.ITCPSendStatusListener;
import by.muna.network.tcp.ITCPServer;
import by.muna.network.tcp.ITCPSocketListener;
import by.muna.network.tcp.TCPServer;
import by.muna.network.tcp.TCPSocket;
import by.muna.network.tcp.TCPSocketsThread;
import by.muna.util.ByteBufferUtil;
import by.muna.zero.server.transport.ITransport;
import by.muna.zero.server.transport.ITransportListener;
import by.muna.zero.server.transport.ITransportPacket;
import by.muna.zero.server.transport.ITransportSendListener;
import by.muna.zero.server.transport.TCPTransport;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TCPTransportTest {
    @Test
    public void basicTest() throws Exception {
        // FIXME: Лапша и содомия с latch'ами

        ITCPServer server = new TCPServer(new InetSocketAddress(0));

        ITransport transport = new TCPTransport(server, 65535);

        int packetsWillSent = 3;

        class Container {
            public int totalPackets = 0;
            public int invalidPackets = 0;

            public Set<Long> longReceived = new HashSet<>();

            public boolean stopped = false;
            public IOException registerException = null;

            public boolean replyReceived = false;

            public int port = 0;
        }
        final Container container = new Container();

        // Отправка и получение
        final CountDownLatch latch = new CountDownLatch(2);

        transport.setListener(new ITransportListener() {
            @Override
            public void onPacket(ITransportPacket packet) {
                container.totalPackets++;

                if (!packet.isValid()) {
                    container.invalidPackets++;
                } else {
                    ByteBuffer buffer = packet.getBuffer();
                    if (buffer.remaining() != 8) {
                        container.invalidPackets++;
                    } else {
                        long got = buffer.getLong();

                        container.longReceived.add(got);

                        if (got == 0x8877665544332211L) {
                            ByteBuffer bufferToSend = ByteBufferUtil.allocateBigEndian(8);

                            bufferToSend.putLong(0x6677881122334455L);
                            bufferToSend.position(0);

                            packet.getUser().send(bufferToSend, new ITransportSendListener() {
                                @Override public void onSent() {}
                                @Override public void onCancelled() {}
                                @Override public void onFail(int bytesSent) {}
                            });
                        }
                    }
                }

                if (container.totalPackets == packetsWillSent) {
                    latch.countDown();
                }
            }

            @Override
            public void onStop() {
                container.stopped = true;
                latch.countDown(); latch.countDown();
            }
        });

        CountDownLatch sendsLatch = new CountDownLatch(1);

        TCPSocketsThread sockets = new TCPSocketsThread();
        sockets.register(server, ex -> {
            if (ex != null) {
                container.registerException = ex;
                sendsLatch.countDown();
                latch.countDown(); latch.countDown();
            } else {
                container.port = server.getPort();

                sendsLatch.countDown();
            }
        });

        sendsLatch.await(50, TimeUnit.MILLISECONDS);

        int port = container.port;
        Assert.assertNotEquals(0, port);

        TCPSocket clientSocket1 = new TCPSocket(new InetSocketAddress(port));
        TCPSocket clientSocket2 = new TCPSocket(new InetSocketAddress(port));

        clientSocket1.setListener(new ITCPSocketListener() {
            @Override public void onConnected() {}
            @Override public void onData(IBufferReadable reader) {
                BufferReadableUtil.clear(reader);
            }
            @Override public void onClosed() {}
        });
        clientSocket2.setListener(new ITCPSocketListener() {
            private ByteBuffer buffer = ByteBufferUtil.allocateBigEndian(4 + 8 + 4);

            @Override public void onConnected() {}
            @Override public void onData(IBufferReadable reader) {
                while (reader.read(buffer) > 0) {
                    if (!buffer.hasRemaining()) {
                        buffer.position(0);

                        if (buffer.getInt() != 12) return;
                        if (buffer.getLong() != 0x6677881122334455L) return;
                        if (buffer.getInt() != 0xaecb4963) return;

                        container.replyReceived = true;

                        latch.countDown();
                    }
                }
            }
            @Override public void onClosed() {}
        });

        Consumer<IOException> registerHandler = ex -> {
            if (ex != null) {
                container.registerException = ex;
                latch.countDown();
            }
        };
        sockets.register(clientSocket1, registerHandler);
        sockets.register(clientSocket2, registerHandler);

        ByteBuffer firstBuffer = ByteBufferUtil.allocateBigEndian(4 + 8 + 4);
        firstBuffer.putInt(12);
        firstBuffer.putLong(0x1122334455667788L);
        firstBuffer.putInt(0x69db0bd0);
        firstBuffer.position(0);

        ByteBuffer secondBuffer = ByteBufferUtil.allocateBigEndian(4 + 4);
        secondBuffer.putInt(4);
        secondBuffer.putInt(1); // not crc32 of 0x0000000c
        secondBuffer.position(0);

        ByteBuffer thirdBuffer = ByteBufferUtil.allocateBigEndian(4 + 8 + 4);
        thirdBuffer.putInt(12);
        thirdBuffer.putLong(0x8877665544332211L);
        thirdBuffer.putInt(0xe5f71b3c);
        thirdBuffer.position(0);

        clientSocket1.requestWriting(firstBuffer, new ITCPSendStatusListener() {
            @Override public void onSent() {} @Override public void onFail(int writed) {}
            @Override public void onCancelled() {}
        });
        clientSocket1.requestWriting(secondBuffer, new ITCPSendStatusListener() {
            @Override public void onSent() {} @Override public void onFail(int writed) {}
            @Override public void onCancelled() {}
        });

        clientSocket2.requestWriting(thirdBuffer, new ITCPSendStatusListener() {
            @Override public void onSent() {} @Override public void onFail(int writed) {}
            @Override public void onCancelled() {}
        });

        latch.await(100, TimeUnit.MILLISECONDS);

        Assert.assertEquals(1, container.invalidPackets);
        Assert.assertEquals(2, container.longReceived.size());

        Assert.assertTrue(container.longReceived.contains(0x1122334455667788L));
        Assert.assertTrue(container.longReceived.contains(0x8877665544332211L));

        Assert.assertTrue(container.replyReceived);
    }
}
