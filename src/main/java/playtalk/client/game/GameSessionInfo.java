package playtalk.client.game;

import playtalk.client.net.ServerConnection;
import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.common.MessageType;

import java.util.List;

/** 게임 창이 알아야 할 정보와 서버로 입력을 보내는 기능. */
public record GameSessionInfo(String sessionId, GameType type, String myNickname,
                              List<String> players, ServerConnection connection) {

    /** GAME_ACTION 메시지 생성 (sessionId 포함) */
    public Message action() {
        return Message.of(MessageType.GAME_ACTION).put("sessionId", sessionId);
    }

    public void send(Message message) {
        connection.send(message);
    }

    public List<String> opponents() {
        return players.stream().filter(p -> !p.equals(myNickname)).toList();
    }
}
