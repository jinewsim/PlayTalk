package playtalk.server;

import playtalk.common.Message;
import playtalk.common.MessageType;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 메시지 타입별 처리기 등록/호출.
 *
 * <p>각 매니저(RoomManager, GameManager 등)가 자기 담당 메시지를 직접 등록한다.
 * 새 기능을 추가할 때 거대한 switch 문을 고칠 필요 없이 {@link #on}으로 처리기만 추가하면 된다.
 */
public class MessageRouter {

    private final Map<MessageType, BiConsumer<ClientHandler, Message>> handlers = new EnumMap<>(MessageType.class);

    public void on(MessageType type, BiConsumer<ClientHandler, Message> handler) {
        if (handlers.containsKey(type)) {
            throw new IllegalStateException(type + " 처리기가 이미 등록되어 있습니다.");
        }
        handlers.put(type, handler);
    }

    public void dispatch(ClientHandler client, Message message) {
        if (!client.isLoggedIn() && message.getType() != MessageType.LOGIN) {
            client.sendError("먼저 닉네임으로 접속해야 합니다.");
            return;
        }
        BiConsumer<ClientHandler, Message> handler = handlers.get(message.getType());
        if (handler == null) {
            client.sendError("지원하지 않는 요청입니다: " + message.getType());
            return;
        }
        handler.accept(client, message);
    }
}
