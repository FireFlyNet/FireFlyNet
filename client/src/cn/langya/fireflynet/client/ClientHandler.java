package cn.langya.fireflynet.client;

import cn.langya.fireflynet.api.Packet;

import java.io.IOException;

/**
 * @author LangYa
 * @since 2024/12/18 00:43
 */
public interface ClientHandler {
    void onConnected() throws IOException;
    void onDisconnected() throws IOException;
    void onPacketRev(Packet packet) throws IOException;
}
