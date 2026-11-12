package playtalk.server.game;

import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;
import playtalk.server.ClientHandler;
import playtalk.server.Log;
import playtalk.server.MessageRouter;
import playtalk.server.Participant;
import playtalk.server.PlayTalkServer;
import playtalk.server.room.ChatRoom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 게임 초대(1:1), 대기실(다인용), 게임 세션 생성/진행/종료를 담당.
 *
 * <p>동시성: 게임 관련 상태는 전부 이 객체 하나의 잠금(synchronized)으로 보호한다.
 * 타이머 작업도 같은 잠금 안에서 실행되므로 GameSession 구현에서는 동기화를 신경 쓰지 않아도 된다.
 */
public class GameManager implements GameContext {

    private static final long LOBBY_EXPIRE_MS = 3 * 60 * 1000L;

    private final PlayTalkServer server;
    private final GameRegistry registry;
    private final ScheduledExecutorService scheduler;

    private final Map<String, Invite> invites = new HashMap<>();
    private final Map<String, GameSession> sessions = new HashMap<>();
    /** roomId → 대기실 */
    private final Map<Integer, Lobby> lobbies = new HashMap<>();
    /** sessionId → 게임이 시작된 채팅방 */
    private final Map<String, ChatRoom> sessionRooms = new HashMap<>();
    private long sequence;

    private record Invite(String id, ClientHandler from, ClientHandler to, GameType type, Cancellable timer) {
    }

    public GameManager(PlayTalkServer server, GameRegistry registry, ScheduledExecutorService scheduler) {
        this.server = server;
        this.registry = registry;
        this.scheduler = scheduler;
    }

    public void registerHandlers(MessageRouter router) {
        router.on(MessageType.GAME_INVITE, this::invite);
        router.on(MessageType.GAME_INVITE_REPLY, this::reply);
        router.on(MessageType.LOBBY_OPEN, this::openLobby);
        router.on(MessageType.LOBBY_JOIN, this::joinLobby);
        router.on(MessageType.LOBBY_LEAVE, this::leaveLobby);
        router.on(MessageType.LOBBY_START, this::startLobby);
        router.on(MessageType.GAME_ACTION, this::action);
        router.on(MessageType.GAME_LEAVE, this::leaveGame);
    }

    // =================================================================== 1:1 초대

    private synchronized void invite(ClientHandler from, Message msg) {
        GameType type = GameType.parse(msg.get("game"));
        if (!registry.supports(type) || !type.isOneOnOne()) {
            from.sendError("1:1로 신청할 수 없는 게임입니다.");
            return;
        }
        ClientHandler to = server.users().find(msg.get("target"));
        String error = checkCanInvite(from, to);
        if (error != null) {
            from.sendError(error);
            return;
        }
        String id = nextId("I");
        Cancellable timer = schedule(() -> expireInvite(id), Protocol.INVITE_TIMEOUT_SECONDS * 1000L);
        invites.put(id, new Invite(id, from, to, type, timer));

        to.send(Message.of(MessageType.GAME_INVITE)
                .put("inviteId", id).put("from", from.nickname())
                .put("game", type.name()).put("timeout", Protocol.INVITE_TIMEOUT_SECONDS));
        from.send(inviteResult(id, to, type, "PENDING"));
        Log.info("게임 초대 %s: %s → %s (%s)", id, from.nickname(), to.nickname(), type);
    }

    private String checkCanInvite(ClientHandler from, ClientHandler to) {
        if (to == null) return "접속 중이 아닌 사용자입니다.";
        if (to == from) return "자기 자신에게는 신청할 수 없습니다.";
        if (from.getRoom() == null || from.getRoom() != to.getRoom()) return "같은 채팅방에 있는 사용자에게만 신청할 수 있습니다.";
        if (from.isInGame()) return "이미 게임 중입니다.";
        if (to.isInGame()) return to.nickname() + " 님은 이미 게임 중입니다.";
        if (hasPendingInvite(from)) return "이미 응답을 기다리는 초대가 있습니다.";
        if (hasPendingInvite(to)) return to.nickname() + " 님이 다른 초대에 응답 중입니다.";
        if (findLobbyOf(from) != null) return "대기실에서 먼저 나가세요.";
        if (findLobbyOf(to) != null) return to.nickname() + " 님은 다인용 게임 대기실에 있습니다.";
        return null;
    }

    private synchronized void reply(ClientHandler user, Message msg) {
        Invite inv = invites.get(msg.get("inviteId"));
        if (inv == null || inv.to() != user) {
            user.sendError("이미 만료된 초대입니다.");
            return;
        }
        invites.remove(inv.id());
        inv.timer().cancel();

        if (!msg.getBoolean("accept")) {
            sendInviteResult(inv, "DECLINED");
            return;
        }
        if (inv.from().isInGame() || inv.to().isInGame() || inv.from().getRoom() == null) {
            sendInviteResult(inv, "CANCELLED");
            return;
        }
        sendInviteResult(inv, "ACCEPTED");
        startSession(inv.type(), List.of(inv.from(), inv.to()), inv.from().getRoom());
    }

    private void expireInvite(String id) {
        Invite inv = invites.remove(id);
        if (inv != null) sendInviteResult(inv, "TIMEOUT");
    }

    private void sendInviteResult(Invite inv, String result) {
        Message m = inviteResult(inv.id(), inv.to(), inv.type(), result).put("from", inv.from().nickname());
        inv.from().send(m);
        inv.to().send(m);
    }

    private Message inviteResult(String id, ClientHandler to, GameType type, String result) {
        return Message.of(MessageType.GAME_INVITE_RESULT)
                .put("inviteId", id).put("target", to.nickname())
                .put("game", type.name()).put("result", result);
    }

    private boolean hasPendingInvite(ClientHandler user) {
        for (Invite inv : invites.values()) {
            if (inv.from() == user || inv.to() == user) return true;
        }
        return false;
    }

    // =================================================================== 다인용 대기실

    private synchronized void openLobby(ClientHandler user, Message msg) {
        GameType type = GameType.parse(msg.get("game"));
        ChatRoom room = user.getRoom();
        if (!registry.supports(type) || type.isOneOnOne()) {
            user.sendError("다인용 게임이 아닙니다.");
            return;
        }
        if (room == null) {
            user.sendError("채팅방에 먼저 입장하세요.");
            return;
        }
        if (lobbies.containsKey(room.getId())) {
            user.sendError("이 방에는 이미 대기실이 열려 있습니다.");
            return;
        }
        if (sessionRooms.containsValue(room) && hasMultiGameIn(room)) {
            user.sendError("이 방에서 다인용 게임이 진행 중입니다.");
            return;
        }
        if (user.isInGame() || hasPendingInvite(user)) {
            user.sendError("진행 중인 게임이나 초대가 있습니다.");
            return;
        }
        if (room.size() < type.minPlayers()) {
            user.sendError(type.displayName() + "은(는) 최소 " + type.minPlayers() + "명이 필요합니다.");
            return;
        }
        Lobby lobby = new Lobby(nextId("L"), type, room, user);
        lobbies.put(room.getId(), lobby);
        lobby.expiry = schedule(() -> closeLobby(lobby, "시간이 지나 대기실이 닫혔습니다.", null), LOBBY_EXPIRE_MS);
        room.broadcast(Message.of(MessageType.SYSTEM)
                .put("text", user.nickname() + " 님이 [" + type.displayName() + "] 대기실을 열었습니다. 참가하세요!"));
        room.broadcast(lobby.stateMessage());
    }

    private synchronized void joinLobby(ClientHandler user, Message msg) {
        Lobby lobby = findLobby(msg.get("lobbyId"));
        if (lobby == null || user.getRoom() != lobby.room) {
            user.sendError("참가할 수 없는 대기실입니다.");
            return;
        }
        if (user.isInGame() || hasPendingInvite(user)) {
            user.sendError("진행 중인 게임이나 초대가 있습니다.");
            return;
        }
        if (lobby.isFull()) {
            user.sendError("대기실 인원이 가득 찼습니다.");
            return;
        }
        if (lobby.players.add(user)) {
            lobby.room.broadcast(lobby.stateMessage());
        }
    }

    private synchronized void leaveLobby(ClientHandler user, Message msg) {
        Lobby lobby = findLobby(msg.get("lobbyId"));
        if (lobby != null) removeFromLobby(lobby, user);
    }

    private void removeFromLobby(Lobby lobby, ClientHandler user) {
        if (lobby.host == user) {
            closeLobby(lobby, "방장이 대기실을 닫았습니다.", user);
        } else if (lobby.players.remove(user)) {
            lobby.room.broadcast(lobby.stateMessage());
            user.send(lobby.stateMessage()); // 방을 나간 사용자도 대기실 창을 갱신하도록
        }
    }

    private synchronized void startLobby(ClientHandler user, Message msg) {
        Lobby lobby = findLobby(msg.get("lobbyId"));
        if (lobby == null || lobby.host != user) {
            user.sendError("대기실 호스트만 시작할 수 있습니다.");
            return;
        }
        List<Participant> players = new ArrayList<>();
        for (ClientHandler p : lobby.players) {
            if (p.getRoom() == lobby.room && !p.isInGame()) players.add(p);
        }
        if (players.size() < lobby.type.minPlayers()) {
            user.sendError("최소 " + lobby.type.minPlayers() + "명이 모여야 시작할 수 있습니다.");
            return;
        }
        closeLobby(lobby, "STARTED", null);
        startSession(lobby.type, players, lobby.room);
    }

    private void closeLobby(Lobby lobby, String reason, ClientHandler alsoNotify) {
        if (lobbies.get(lobby.room.getId()) != lobby) return;
        lobbies.remove(lobby.room.getId());
        if (lobby.expiry != null) lobby.expiry.cancel();
        Message closed = Message.of(MessageType.LOBBY_CLOSED).put("lobbyId", lobby.id).put("reason", reason);
        Set<ClientHandler> targets = new LinkedHashSet<>(lobby.room.members());
        targets.addAll(lobby.players);
        if (alsoNotify != null) targets.add(alsoNotify);
        for (ClientHandler c : targets) c.send(closed);
    }

    private Lobby findLobby(String lobbyId) {
        for (Lobby l : lobbies.values()) {
            if (l.id.equals(lobbyId)) return l;
        }
        return null;
    }

    private Lobby findLobbyOf(ClientHandler user) {
        for (Lobby l : lobbies.values()) {
            if (l.players.contains(user)) return l;
        }
        return null;
    }

    private boolean hasMultiGameIn(ChatRoom room) {
        for (Map.Entry<String, ChatRoom> e : sessionRooms.entrySet()) {
            GameSession s = sessions.get(e.getKey());
            if (e.getValue() == room && s != null && !s.getType().isOneOnOne()) return true;
        }
        return false;
    }

    // =================================================================== 세션 진행

    private void startSession(GameType type, List<? extends Participant> players, ChatRoom room) {
        String id = nextId("G");
        List<Participant> list = new ArrayList<>(players);
        GameSession session = registry.create(type, id, list, this);
        sessions.put(id, session);
        sessionRooms.put(id, room);

        List<String> names = new ArrayList<>();
        for (Participant p : list) {
            names.add(p.nickname());
            if (p instanceof ClientHandler c) c.setGameSessionId(id);
        }
        Message start = Message.of(MessageType.GAME_START)
                .put("sessionId", id).put("game", type.name()).putList("players", names);
        session.fillStartMessage(start);
        for (Participant p : list) p.send(start);

        Log.info("게임 시작 %s %s %s", id, type, names);
        if (room != null) {
            room.broadcast(Message.of(MessageType.SYSTEM)
                    .put("text", "[" + type.displayName() + "] " + String.join(" vs ", names) + " 게임이 시작되었습니다!"));
        }
        refreshMembers(list);
        session.start();
    }

    private synchronized void action(ClientHandler user, Message msg) {
        GameSession session = sessions.get(msg.get("sessionId"));
        if (session == null || !session.getPlayers().contains(user)) {
            user.sendError("진행 중인 게임이 아닙니다.");
            return;
        }
        session.onAction(user, msg);
    }

    private synchronized void leaveGame(ClientHandler user, Message msg) {
        GameSession session = sessions.get(msg.get("sessionId"));
        if (session == null) return;
        leaveSession(session, user);
    }

    private void leaveSession(GameSession session, ClientHandler user) {
        session.onPlayerLeft(user);
        if (session.getId().equals(user.getGameSessionId())) {
            user.setGameSessionId(null);
            refreshMembers(List.of(user));
        }
    }

    /** GameContext: 세션이 끝났을 때 */
    @Override
    public synchronized void finish(GameSession session, GameResult result) {
        if (sessions.remove(session.getId()) == null) return;
        ChatRoom room = sessionRooms.remove(session.getId());

        for (Participant p : session.getPlayers()) {
            if (p instanceof ClientHandler c && session.getId().equals(c.getGameSessionId())) {
                c.setGameSessionId(null);
            }
        }
        server.stats().record(session.getType(), result);

        List<String[]> rows = new ArrayList<>();
        result.outcomes().forEach((nick, outcome) -> rows.add(new String[]{nick, outcome}));
        Message end = Message.of(MessageType.GAME_END)
                .put("sessionId", session.getId()).put("game", session.getType().name())
                .putTable("results", rows).put("summary", result.summary());
        for (Participant p : session.getPlayers()) p.send(end);

        Log.info("게임 종료 %s: %s", session.getId(), result.summary());
        if (room != null) {
            room.broadcast(Message.of(MessageType.SYSTEM)
                    .put("text", "[결과] [" + session.getType().displayName() + "] " + result.summary()));
        }
        refreshMembers(session.getPlayers());
    }

    /** GameContext: 게임 타이머 */
    @Override
    public Cancellable schedule(GameSession session, Runnable task, long delayMs) {
        return schedule(() -> {
            if (!session.isFinished()) task.run();
        }, delayMs);
    }

    private Cancellable schedule(Runnable task, long delayMs) {
        ScheduledFuture<?> f = scheduler.schedule(() -> {
            synchronized (GameManager.this) {
                try {
                    task.run();
                } catch (Exception e) {
                    Log.error("게임 타이머 작업 오류: %s", e);
                    e.printStackTrace();
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        return () -> f.cancel(false);
    }

    // =================================================================== 퇴장 / 연결 끊김

    /** RoomManager 퇴장 리스너: 대기실에서도 빼 준다. 진행 중인 게임은 그대로 계속한다. */
    public synchronized void onRoomLeave(ClientHandler user, ChatRoom room) {
        Lobby lobby = lobbies.get(room.getId());
        if (lobby != null) removeFromLobby(lobby, user);
    }

    /** 연결이 끊긴 사용자: 초대 취소, 대기실 퇴장, 게임 기권 처리 */
    public synchronized void onDisconnect(ClientHandler user) {
        for (Invite inv : new ArrayList<>(invites.values())) {
            if (inv.from() == user || inv.to() == user) {
                invites.remove(inv.id());
                inv.timer().cancel();
                sendInviteResult(inv, "CANCELLED");
            }
        }
        Lobby lobby = findLobbyOf(user);
        if (lobby != null) removeFromLobby(lobby, user);

        GameSession session = sessions.get(user.getGameSessionId());
        if (session != null) leaveSession(session, user);
    }

    // =================================================================== util

    private void refreshMembers(List<? extends Participant> players) {
        Set<ChatRoom> rooms = new LinkedHashSet<>();
        for (Participant p : players) {
            if (p instanceof ClientHandler c && c.getRoom() != null) rooms.add(c.getRoom());
        }
        for (ChatRoom r : rooms) server.rooms().broadcastMembers(r);
    }

    private String nextId(String prefix) {
        return prefix + (++sequence);
    }
}
