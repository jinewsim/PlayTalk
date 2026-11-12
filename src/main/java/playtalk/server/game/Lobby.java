package playtalk.server.game;

import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.server.ClientHandler;
import playtalk.server.room.ChatRoom;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 다인용 게임 대기실. 채팅방 하나에 최대 하나만 열린다. GameManager 잠금 안에서만 사용. */
class Lobby {

    final String id;
    final GameType type;
    final ChatRoom room;
    final ClientHandler host;
    final Set<ClientHandler> players = new LinkedHashSet<>();
    GameContext.Cancellable expiry;

    Lobby(String id, GameType type, ChatRoom room, ClientHandler host) {
        this.id = id;
        this.type = type;
        this.room = room;
        this.host = host;
        players.add(host);
    }

    boolean isFull() {
        return players.size() >= type.maxPlayers();
    }

    Message stateMessage() {
        List<String> names = new ArrayList<>();
        for (ClientHandler p : players) names.add(p.nickname());
        return Message.of(MessageType.LOBBY_STATE)
                .put("lobbyId", id)
                .put("roomId", room.getId())
                .put("game", type.name())
                .put("host", host.nickname())
                .put("min", type.minPlayers())
                .put("max", type.maxPlayers())
                .putList("players", names);
    }
}
