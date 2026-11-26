package playtalk.client.game;

import playtalk.client.net.ServerConnection;
import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;

import javax.swing.JFrame;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;


/**
 * 클라이언트 쪽 게임 흐름 담당: 초대 주고받기, 대기실, 게임 창 열기/전달.
 * 게임 종류와 관계없이 동작하므로 새 게임을 추가해도 이 클래스는 고칠 필요가 없다.
 */
public class GameController {

    private final JFrame frame;
    private final ServerConnection connection;
    private final String me;
    private final Map<String, GameWindow> windows = new HashMap<>();
    /** 게임이 끝났지만 아직 화면에 열려 있는 창들 (재대결 시작 때 닫기 위해 보관) */
    private final List<GameWindow> endedWindows = new ArrayList<>();
    private IntConsumer statsOpener;
    /** 지금 화면에 보이는 채팅방 (홈이면 -1). 다른 방의 대기실 창이 갑자기 뜨지 않게 거른다 */
    private int viewedRoomId = -1;
    private Consumer<String> versusOpener;

    /** 초대 결과 등 안내 문구를 보여줄 곳 (기본: 채팅창 시스템 메시지) */
    private Consumer<String> notifier;
    private final Map<String, InviteDialog> invites = new HashMap<>();
    private final Map<String, WaitingDialog> waiting = new HashMap<>();
    private final LobbyDialog lobbyDialog;
    /** 참여 중인 방마다 마지막으로 받은 대기실 상태 (방을 오가도 대기실 정보를 잃지 않도록) */
    private final Map<Integer, Message> lobbyByRoom = new HashMap<>();
    /** 지금 보고 있는 방에 열려 있는 대기실 id (없으면 null) */
    private String currentLobbyId;

    public GameController(JFrame frame, ServerConnection connection, String me) {
        this.frame = frame;
        this.connection = connection;
        this.me = me;
        this.notifier = text -> { };
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
        GameType type = GameChoiceDialog.choose(frame, "1:1 대결 신청", target + " 님과 어떤 게임을 할까요?", options);
        if (type == null) return;
        connection.send(Message.of(MessageType.GAME_INVITE).put("target", target).put("game", type.name()));
    }

    /** 다인용 게임: 대기실이 있으면 보여주고, 없으면 새로 연다 */
    public void openMultiGame(int roomId) {
        if (currentLobbyId != null) {
            lobbyDialog.setVisible(true);
            return;
        }
        GameType[] options = Arrays.stream(GameType.values()).filter(t -> !t.isOneOnOne()).toArray(GameType[]::new);
        GameType type = GameChoiceDialog.choose(frame, "다인용 게임", "방 사람들과 함께할 게임을 고르세요.", options);
        if (type == null) return;
        connection.send(Message.of(MessageType.LOBBY_OPEN).put("game", type.name()).put("roomId", roomId));
    }

    /** 보고 있는 방이 바뀌면(방 전환, 홈으로 이동) 이전 방의 대기실 창은 닫는다 */
    public void onRoomChanged(int viewedRoomId) {
        this.viewedRoomId = viewedRoomId;
        currentLobbyId = null;
        lobbyDialog.setVisible(false);
        Message lobby = lobbyByRoom.get(viewedRoomId);
        if (lobby == null) return;
        // 돌아온 방에 대기실이 열려 있으면 정보를 되살린다. 내가 참가한 대기실이면 창도 다시 띄운다
        currentLobbyId = lobby.get("lobbyId");
        lobbyDialog.update(lobby);
        if (lobby.getList("players").contains(me)) lobbyDialog.setVisible(true);
    }

    /** 방에서 나갔거나 새로 들어갈 때, 그 방의 예전 대기실 정보를 지운다 */
    public void forgetRoom(int roomId) {
        lobbyByRoom.remove(roomId);
    }
    /** 게임 창의 "전적 보기" 버튼이 호출할 동작 (메인 화면의 전적 창 열기) */
    /** 방 밖(홈 화면)에 있을 때는 안내 문구를 홈에 띄우도록 바꿀 수 있다 */
    public void setNotifier(Consumer<String> notifier) {
        this.notifier = notifier;
    }

    public void setStatsOpener(IntConsumer statsOpener) {
        this.statsOpener = statsOpener;
    }

    public void setVersusOpener(Consumer<String> versusOpener) {
        this.versusOpener = versusOpener;
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
        WaitingDialog wait = waiting.remove(msg.get("inviteId"));
        if (wait != null) wait.close();

        boolean iAmInviter = me.equals(msg.get("from")) || !msg.has("from");
        GameType type = GameType.parse(msg.get("game"));
        String game = type == null ? msg.get("game") : type.displayName();
        String target = msg.get("target");
        switch (msg.get("result")) {
            case "PENDING" -> {
                notifier.accept(target + " 님에게 [" + game + "] 대결을 신청했습니다. 응답을 기다리는 중...");
                String inviteId = msg.get("inviteId");
                WaitingDialog w = new WaitingDialog(frame, target, game, Protocol.INVITE_TIMEOUT_SECONDS,
                        () -> connection.send(Message.of(MessageType.GAME_INVITE_CANCEL).put("inviteId", inviteId)));
                waiting.put(inviteId, w);
                w.setVisible(true);
            }
            case "DECLINED" -> notifier.accept(iAmInviter ? target + " 님이 대결을 거절했습니다." : "대결 신청을 거절했습니다.");
            case "TIMEOUT" -> notifier.accept(iAmInviter ? target + " 님이 제한 시간 안에 응답하지 않았습니다." : "응답 시간이 지나 초대가 취소되었습니다.");
            case "CANCELLED" -> notifier.accept("대결 신청이 취소되었습니다.");
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 대기실

    private void onLobbyState(Message msg) {
        int roomId = msg.getInt("roomId", -1);
        lobbyByRoom.put(roomId, msg);
        if (roomId != viewedRoomId) return; // 다른 방의 대기실은 기억해 두었다가 그 방을 열 때 보여준다
        boolean firstTime = !msg.get("lobbyId").equals(currentLobbyId);
        currentLobbyId = msg.get("lobbyId");
        lobbyDialog.update(msg);
        boolean joined = msg.getList("players").contains(me);
        if (firstTime || joined) {
            if (!lobbyDialog.isVisible()) lobbyDialog.setVisible(true);
        }
    }

    private void onLobbyClosed(Message msg) {
        int roomId = msg.getInt("roomId", -1);
        Message cached = lobbyByRoom.get(roomId);
        if (cached != null && cached.get("lobbyId").equals(msg.get("lobbyId"))) lobbyByRoom.remove(roomId);
        if (roomId >= 0 && roomId != viewedRoomId) return; // 보고 있지 않은 방의 대기실 안내는 띄우지 않는다
        if (msg.get("lobbyId").equals(currentLobbyId)) {
            currentLobbyId = null;
            lobbyDialog.setVisible(false);
        }
        if (!"STARTED".equals(msg.get("reason"))) notifier.accept(msg.get("reason"));
    }

    // ------------------------------------------------------------------ 게임 진행

    private void onGameStart(Message msg) {
        GameType type = GameType.parse(msg.get("game"));
        if (type == null) return;
        int roomId = msg.getInt("roomId", -1);
        GameSessionInfo info = new GameSessionInfo(msg.get("sessionId"), type, me, msg.getList("players"), connection, roomId);
        closeEndedWindows(info);
        GameWindow window = GameWindowRegistry.create(frame, info);
        // 1:1 게임이면 그 상대와 한 전적, 다인용이면 그 채팅방의 전적
        String opponent = null;
        if (type.isOneOnOne()) {
            for (String p : info.players()) {
                if (!p.equals(me)) opponent = p;
            }
        }
        final String opp = opponent;
        window.setOnShowStats(() -> {
            if (opp != null) {
                if (versusOpener != null) versusOpener.accept(opp);
            } else if (statsOpener != null) {
                statsOpener.accept(roomId);
            }
        });
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
        if (w != null && w.isDisplayable()) {
            w.onEnd(msg);
            endedWindows.add(w);
        }
    }

    /** 같은 게임의 새 게임이 시작되면 이전 결과 창을 닫는다 (1:1은 같은 참가자일 때만) */
    private void closeEndedWindows(GameSessionInfo next) {
        endedWindows.removeIf(w -> !w.isDisplayable());
        endedWindows.removeIf(w -> {
            boolean same = w.session.type() == next.type()
                    && (!next.type().isOneOnOne()
                    || new HashSet<>(w.session.players()).equals(new HashSet<>(next.players())));
            if (same) w.dispose();
            return same;
        });
    }

}
