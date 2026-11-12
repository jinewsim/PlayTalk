package playtalk.client.game;

import playtalk.client.net.ServerConnection;
import playtalk.client.ui.ChatPanel;
import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.common.MessageType;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 클라이언트 쪽 게임 흐름 담당: 초대 주고받기, 대기실, 게임 창 열기/전달.
 * 게임 종류와 관계없이 동작하므로 새 게임을 추가해도 이 클래스는 고칠 필요가 없다.
 */
public class GameController {

    private final JFrame frame;
    private final ServerConnection connection;
    private final String me;
    private final ChatPanel chat;
    private final Map<String, GameWindow> windows = new HashMap<>();
    private final Map<String, InviteDialog> invites = new HashMap<>();
    private final LobbyDialog lobbyDialog;
    /** 지금 내 방에 열려 있는 대기실 id (없으면 null) */
    private String currentLobbyId;

    public GameController(JFrame frame, ServerConnection connection, String me, ChatPanel chat) {
        this.frame = frame;
        this.connection = connection;
        this.me = me;
        this.chat = chat;
        this.lobbyDialog = new LobbyDialog(frame, connection, me);

        connection.on(MessageType.GAME_INVITE, this::onInvite);
        connection.on(MessageType.GAME_INVITE_RESULT, this::onInviteResult);
        connection.on(MessageType.LOBBY_STATE, this::onLobbyState);
        connection.on(MessageType.LOBBY_CLOSED, this::onLobbyClosed);
        connection.on(MessageType.GAME_START, this::onGameStart);
        connection.on(MessageType.GAME_STATE, this::onGameState);
        connection.on(MessageType.GAME_END, this::onGameEnd);
    }

    // ------------------------------------------------------------------ 사용자 동작

    /** 1:1 대결 신청 (게임 선택 후 초대 전송) */
    public void challenge(String target) {
        GameType[] options = Arrays.stream(GameType.values()).filter(GameType::isOneOnOne).toArray(GameType[]::new);
        GameType type = (GameType) JOptionPane.showInputDialog(frame, target + " 님과 어떤 게임을 할까요?",
                "1:1 대결 신청", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (type == null) return;
        connection.send(Message.of(MessageType.GAME_INVITE).put("target", target).put("game", type.name()));
    }

    /** 다인용 게임: 대기실이 있으면 보여주고, 없으면 새로 연다 */
    public void openMultiGame() {
        if (currentLobbyId != null) {
            lobbyDialog.setVisible(true);
            return;
        }
        GameType[] options = Arrays.stream(GameType.values()).filter(t -> !t.isOneOnOne()).toArray(GameType[]::new);
        GameType type = (GameType) JOptionPane.showInputDialog(frame, "방 사람들과 함께할 게임을 고르세요.",
                "다인용 게임", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (type == null) return;
        connection.send(Message.of(MessageType.LOBBY_OPEN).put("game", type.name()));
    }

    /** 방을 옮기면 이전 방의 대기실 정보는 버린다 */
    public void onRoomChanged() {
        currentLobbyId = null;
        lobbyDialog.setVisible(false);
    }

    /** ERROR를 게임 창에서 보여줬으면 true */
    public boolean showErrorInGame(String text) {
        for (GameWindow w : windows.values()) {
            if (w.isActive() && !w.isEnded()) {
                w.showError(text);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 초대

    private void onInvite(Message msg) {
        String id = msg.get("inviteId");
        GameType type = GameType.parse(msg.get("game"));
        InviteDialog dialog = new InviteDialog(frame, msg.get("from"),
                type == null ? msg.get("game") : type.displayName(), msg.getInt("timeout", 15),
                accept -> {
                    invites.remove(id);
                    connection.send(Message.of(MessageType.GAME_INVITE_REPLY).put("inviteId", id).put("accept", accept));
                });
        invites.put(id, dialog);
        dialog.setVisible(true);
        frame.toFront();
        dialog.toFront();
    }

    private void onInviteResult(Message msg) {
        InviteDialog dialog = invites.remove(msg.get("inviteId"));
        if (dialog != null) dialog.close();

        boolean iAmInviter = me.equals(msg.get("from")) || !msg.has("from");
        GameType type = GameType.parse(msg.get("game"));
        String game = type == null ? msg.get("game") : type.displayName();
        String target = msg.get("target");
        switch (msg.get("result")) {
            case "PENDING" -> chat.appendSystem(target + " 님에게 [" + game + "] 대결을 신청했습니다. 응답을 기다리는 중...");
            case "DECLINED" -> chat.appendSystem(iAmInviter ? target + " 님이 대결을 거절했습니다." : "대결 신청을 거절했습니다.");
            case "TIMEOUT" -> chat.appendSystem(iAmInviter ? target + " 님이 제한 시간 안에 응답하지 않았습니다." : "응답 시간이 지나 초대가 취소되었습니다.");
            case "CANCELLED" -> chat.appendSystem("대결 신청이 취소되었습니다.");
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 대기실

    private void onLobbyState(Message msg) {
        boolean firstTime = !msg.get("lobbyId").equals(currentLobbyId);
        currentLobbyId = msg.get("lobbyId");
        lobbyDialog.update(msg);
        boolean joined = msg.getList("players").contains(me);
        if (firstTime || joined) {
            if (!lobbyDialog.isVisible()) lobbyDialog.setVisible(true);
        }
    }

    private void onLobbyClosed(Message msg) {
        if (msg.get("lobbyId").equals(currentLobbyId)) {
            currentLobbyId = null;
            lobbyDialog.setVisible(false);
        }
        if (!"STARTED".equals(msg.get("reason"))) chat.appendSystem(msg.get("reason"));
    }

    // ------------------------------------------------------------------ 게임 진행

    private void onGameStart(Message msg) {
        GameType type = GameType.parse(msg.get("game"));
        if (type == null) return;
        GameSessionInfo info = new GameSessionInfo(msg.get("sessionId"), type, me, msg.getList("players"), connection);
        GameWindow window = GameWindowRegistry.create(frame, info);
        windows.put(info.sessionId(), window);
        window.setVisible(true);
        window.onStart(msg);
    }

    private void onGameState(Message msg) {
        GameWindow w = windows.get(msg.get("sessionId"));
        if (w != null) w.onState(msg);
    }

    private void onGameEnd(Message msg) {
        GameWindow w = windows.remove(msg.get("sessionId"));
        if (w != null && w.isDisplayable()) w.onEnd(msg);
    }
}
