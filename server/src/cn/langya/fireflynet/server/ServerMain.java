package cn.langya.fireflynet.server;

import cn.langya.fireflynet.api.Packet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author LangYa
 * @since 2024/12/7 16:01
 */
public class ServerMain {
    private final int port;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private final ServerHandler serverHandler;
    private final int maxBytes;

    private final ConcurrentHashMap<SocketChannel, String> clients = new ConcurrentHashMap<>();

    public ServerMain(int port, int maxBytes, ServerHandler serverHandler) {
        this.port = port;
        this.maxBytes = maxBytes;
        Packet.maxBytes = maxBytes - 8;
        this.serverHandler = serverHandler;
    }

    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started on port " + port);

        while (true) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (key.isAcceptable()) {
                    acceptConnection();
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        // 唯一标识aa
        String clientAddress = clientChannel.getRemoteAddress().toString();
        clients.put(clientChannel, clientAddress);

        System.out.println("Client connected: " + clientAddress);
    }

    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(maxBytes);

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                Packet packet = Packet.decode(buffer);

                // 如果数据包解码失败 忽略且继续aa
                if (packet == null) {
                    System.err.println("Received invalid packet from " + clients.get(clientChannel));
                    return;
                }

                serverHandler.onClientPacketRev(packet);
            } else if (bytesRead == -1) {
                disconnectClient(clientChannel, key);
            }
        } catch (IOException e) {
            System.out.println("Error reading from client: " + clients.get(clientChannel));
            disconnectClient(clientChannel, key);
        }
    }

    private void disconnectClient(SocketChannel clientChannel, SelectionKey key) {
        try {
            String clientAddress = clients.remove(clientChannel);
            if (key != null) {
                key.cancel();
            }
            clientChannel.close();
            // 这个错误太低级了吧..我是傻逼
            clients.remove(clientChannel);
            serverHandler.onClientDisconnected(clientAddress);
        } catch (IOException e) {
            System.err.println("Failed to disconnect client: " + clients.get(clientChannel) + " - " + e.getMessage());
        }
    }

    public void sendToClient(SocketChannel client, Packet packet) throws IOException {
        ByteBuffer buffer = packet.encode();
        client.write(buffer);
        System.out.println("Sent packet to " + clients.get(client) + ": ID=" + packet.getId());
    }

    public void broadcast(Packet packet) {
        for (SocketChannel client : clients.keySet()) {
            if (client.isConnected()) {
                try {
                    sendToClient(client, packet);
                } catch (IOException e) {
                    System.err.println("Failed to send packet to client: " + clients.get(client) + " - " + e.getMessage());
                }
            }
        }
        System.out.println("Broadcast packet: ID=" + packet.getId());
    }

    public void stop() throws IOException {
        for (SocketChannel client : clients.keySet()) {
            client.close();
        }

        clients.clear();

        if (serverChannel != null) serverChannel.close();
        if (selector != null) selector.close();
        serverHandler.onStop();
    }
}
