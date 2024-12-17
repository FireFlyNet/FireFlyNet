package cn.langya.fireflynet.server;

import cn.langya.fireflynet.api.Packet;
import java.io.IOException;

/**
 * @author LangYa
 * @since 2024/12/7 17:58
 */
public interface ServerHandler {
    void onClientPacketRev(Packet packet) throws IOException;
    void onClientDisconnected(String clientAddress) throws IOException;
    void onStop() throws IOException;
}
