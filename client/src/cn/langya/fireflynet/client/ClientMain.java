package cn.langya.fireflynet.client;

import cn.langya.fireflynet.api.Packet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * @author LangYa
 * @since 2024/12/7 16:06
 */
public class ClientMain {
    private final String host;
    private final int port;
    private final ClientHandler clientHandler;
    private Selector selector;
    private SocketChannel channel;
    private volatile boolean running = true;

    public ClientMain(String host, int port, ClientHandler clientHandler) throws IOException {
        this.host = host;
        this.port = port;
        this.clientHandler = clientHandler;
        connect();
    }

    private void connect() throws IOException {
        selector = Selector.open();
        channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(host, port));
        channel.register(selector, SelectionKey.OP_CONNECT);

        new Thread(this::runEventLoop, "Client-EventLoop").start();
    }

    private void runEventLoop() {
        try {
            while (running) {
                // Wait for event
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (key.isConnectable()) {
                        finishConnection(key);
                    } else if (key.isReadable()) {
                        readFromServer(key);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error in event loop: " + e.getMessage());
        } finally {
            try {
                close();
            } catch (IOException e) {
                System.err.println("Error closing client: " + e.getMessage());
            }
        }
    }

    private void finishConnection(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        if (clientChannel.finishConnect()) {
            clientChannel.register(selector, SelectionKey.OP_READ);
            clientHandler.onConnected();
        }
    }

    private void readFromServer(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                Packet packet = Packet.decode(buffer);
                if (packet != null) clientHandler.onPacketRev(packet);
            } else if (bytesRead == -1) {
                close();
            }
        } catch (IOException e) {
            System.out.println("Error reading from server: " + e.getMessage());
        }
    }

    public void sendPacket(Packet packet) throws IOException {
        if (channel != null && channel.isConnected()) {
            ByteBuffer buffer = packet.encode();
            channel.write(buffer);
        }
    }

    public void close() throws IOException {
        running = false;
        if (channel != null) channel.close();
        if (selector != null) selector.close();
        clientHandler.onDisconnected();
    }
}
