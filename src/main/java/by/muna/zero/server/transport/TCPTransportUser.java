package by.muna.zero.server.transport;

import by.muna.buffers.IBufferReadable;
import by.muna.network.tcp.ITCPSendStatusListener;
import by.muna.network.tcp.ITCPSocket;
import by.muna.network.tcp.ITCPSocketListener;
import by.muna.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import java.util.zip.CRC32;

public class TCPTransportUser implements ITransportUser {
    private TCPTransport transport;
    private ITCPSocket socket;

    private ByteBuffer headerBuffer = ByteBufferUtil.allocateBigEndian(4);
    private ByteBuffer packetBuffer = null;

    public TCPTransportUser(TCPTransport transport, ITCPSocket socket) {
        this.transport = transport;
        this.socket = socket;
        this.socket.setListener(new ITCPSocketListener() {
            @Override public void onConnected() {}

            @Override
            public void onData(IBufferReadable reader) {
                TCPTransportUser.this.onData(reader);
            }

            @Override
            public void onClosed() {}
        });
    }

    private void onData(IBufferReadable reader) {
        // FIXME: control flow шибко запутанный, хоть и выглядит правильным

        while (true) {
            receivingHeader:
            if (TCPTransportUser.this.packetBuffer == null) {
                while (reader.read(this.headerBuffer) > 0) {
                    if (!this.headerBuffer.hasRemaining()) {
                        this.headerBuffer.position(1);

                        int packetLength = 0;
                        packetLength += (this.headerBuffer.get() & 0xFF) << 16;
                        packetLength += (this.headerBuffer.get() & 0xFF) << 8;
                        packetLength += this.headerBuffer.get() & 0xFF;

                        if (packetLength <= this.transport.maxPacketLength && packetLength >= 4) {
                            this.packetBuffer = ByteBufferUtil.allocateBigEndian(packetLength);
                            this.headerBuffer.position(0);
                        } else {
                            // Сейчас packetBuffer == null, и !headerBuffer.hasRemaining(),
                            // поэтому если библиотека сокетов вызовет этот метод снова,
                            // то просто произойдёт reader.read, который вернёт сразу ноль и всё,
                            // поэтому не нужно никак помечать этого юзера как закрытого.
                            socket.close();
                            return;
                        }

                        break receivingHeader;
                    }
                }

                return;
            }

            receivingBody: {
                while (reader.read(this.packetBuffer) > 0) {
                    if (!this.packetBuffer.hasRemaining()) {
                        this.packetBuffer.position(0);

                        int length = this.packetBuffer.limit();
                        ByteBuffer packetBufferToCRC32 = this.packetBuffer.asReadOnlyBuffer();
                        packetBufferToCRC32.limit(length - 4);

                        CRC32 crc32 = new CRC32();
                        crc32.update(this.headerBuffer.asReadOnlyBuffer());
                        crc32.update(packetBufferToCRC32);

                        long expectedCRC32Value = crc32.getValue();
                        long actualCRC32Value = this.packetBuffer.getInt(length - 4) & 0xFFFFFFFFL;

                        boolean valid = expectedCRC32Value == actualCRC32Value;

                        this.packetBuffer.limit(length - 4);

                        this.transport.onPacket(new TransportPacket(
                            this, this.packetBuffer, valid
                        ));

                        this.packetBuffer = null;

                        if (!valid) {
                            // Перемещаем позицию в конец, чтобы при считывании всегда выдавало ноль,
                            // на всякий случай.
                            this.headerBuffer.position(4);
                            this.socket.close();
                            return;
                        }

                        break receivingBody;
                    }
                }

                return;
            }
        }
    }

    @Override
    public void send(Supplier<ByteBuffer> bufferProvider, ITransportSendListener listener) {
        this.socket.requestWriting(() -> {
            ByteBuffer buffer = bufferProvider.get();

            // TODO: или реализовать ByteBuffer, который может объединять несколько,
            // или добавить Yasly'ям фичу отправлять несколько буфферов в одном запросе,
            // копирование — зло.
            ByteBuffer totalBuffer = ByteBufferUtil.allocateBigEndian(4 + buffer.remaining() + 4);
            totalBuffer.putInt(buffer.remaining() + 4);
            totalBuffer.put(buffer);

            ByteBuffer crc32Buffer = totalBuffer.asReadOnlyBuffer();
            crc32Buffer.flip();

            CRC32 crc32 = new CRC32();
            crc32.update(crc32Buffer);
            long crc32Value = crc32.getValue();

            totalBuffer.putInt((int) crc32Value);

            totalBuffer.position(0);

            return totalBuffer;
        }, new ITCPSendStatusListener() {
            @Override public void onSent() {
                listener.onSent();
            }
            @Override public void onCancelled() {
                listener.onCancelled();
            }
            @Override public void onFail(int writed) {
                listener.onFail(writed);
            }
        });
    }

    @Override
    public void bulk(Runnable runnable) {
        // TODO: реализовать bulk-режим.
        runnable.run();
    }

    @Override
    public void end() {
        this.socket.close();
    }
}
