package by.muna.zero.server.transport;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.function.Supplier;

public interface ITransportUser {
    /**
     * Все вызовы методов {@code send} из {@code runnable}
     * будут отправлены минимальным количеством физических пакетов.
     * @param runnable
     */
    void bulk(Runnable runnable);

    /**
     * Отправляет пользователю пакет.
     * @param bufferProvider Провайдер пакета. Если вернёт null, ничего отправлено не будет,
     *                       а так же будет вызван listener.onCancelled()
     * @param listener
     */
    void send(Supplier<ByteBuffer> bufferProvider, ITransportSendListener listener);
    default void send(ByteBuffer buffer, ITransportSendListener listener) {
        this.send(() -> buffer, listener);
    }

    default void bulkSend(Iterator<ITransportSendRequest> requests) {
        this.bulk(() -> {
            while (requests.hasNext()) {
                ITransportSendRequest sendRequest = requests.next();

                this.send(sendRequest.getBufferSupplier(), sendRequest.getSendListener());
            }
        });
    }

    /**
     * Если транспорт использует соединения (к примеру, TCP), то вызов этого метода закроет его.
     */
    void end();
}
