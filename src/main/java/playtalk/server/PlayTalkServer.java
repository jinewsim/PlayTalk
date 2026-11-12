package playtalk.server;

import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;
import playtalk.server.game.GameManager;
import playtalk.server.game.GameRegistry;
import playtalk.server.room.RoomManager;
import playtalk.server.stats.StatsStore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * PlayTalk 서버.
 *
 * <p>구조
 * <ul>
 *   <li>메인 스레드: {@link ServerSocket#accept()}로 접속을 받아 클라이언트마다 {@link ClientHandler} 스레드 생성</li>
 *   <li>{@link MessageRouter}: 메시지 타입별로 담당 매니저에 전달</li>
 *   <li>{@link UserManager} 접속자 / {@link RoomManager} 채팅방 / {@link GameManager} 게임 / {@link StatsStore} 전적</li>
 * </ul>
 */
public class PlayTalkServer {

    private final int port;
    private final MessageRouter router = new MessageRouter();
    private final UserManager users = new UserManager();
    private final RoomManager rooms;
    private final GameManager games;
    private final StatsStore stats;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile ServerSocket serverSocket;

    public PlayTalkServer(int port, Path dataDir) {
        this.port = port;
        this.stats = new StatsStore(dataDir.resolve("stats.csv"));
        this.rooms = new RoomManager(this);
        this.games = new GameManager(this, new GameRegistry(), scheduler);

        router.on(MessageType.LOGIN, this::handleLogin);
        router.on(MessageType.STATS_REQUEST, (user, msg) ->
                user.send(stats.statsMessage(msg.get("nickname", user.nickname()))));
        rooms.registerHandlers(router);
        games.registerHandlers(router);
        rooms.addLeaveListener(games::onRoomLeave);
    }

    /** 서버 실행 (stop()이 호출될 때까지 반환하지 않음) */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        Log.info("PlayTalk 서버 시작 (포트 %d)", port);
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(socket, this);
                Log.info("연결됨: %s", handler.remoteAddress());
                clientPool.execute(handler);
            } catch (IOException e) {
                if (!serverSocket.isClosed()) Log.error("접속 처리 실패: %s", e.getMessage());
            }
        }
    }

    public void stop() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        for (ClientHandler c : users.all()) c.close();
        clientPool.shutdownNow();
        scheduler.shutdownNow();
    }

    // ------------------------------------------------------------------ login / logout

    private void handleLogin(ClientHandler client, Message msg) {
        if (client.isLoggedIn()) return;
        String nickname = msg.get("nickname").trim();
        String error = Protocol.validateNickname(nickname);
        if (error == null && !users.register(nickname, client)) {
            error = "이미 사용 중인 닉네임입니다. 다른 닉네임을 입력하세요.";
        }
        if (error != null) {
            client.send(Message.of(MessageType.LOGIN_FAIL).put("reason", error));
            return;
        }
        client.setNickname(nickname);
        Log.info("로그인: %s (%s)", nickname, client.remoteAddress());
        client.send(Message.of(MessageType.LOGIN_OK).put("nickname", nickname));
        client.send(rooms.roomListMessage());
        broadcastUserList();
    }

    /** ClientHandler 스레드가 끝날 때 호출 (정상 종료, 비정상 끊김 모두) */
    void onDisconnect(ClientHandler client) {
        if (!client.isLoggedIn()) return;
        rooms.leave(client);
        games.onDisconnect(client);
        users.remove(client);
        Log.info("접속 종료: %s", client.nickname());
        broadcastUserList();
    }

    // ------------------------------------------------------------------ broadcast

    public void broadcastAll(Message message) {
        for (ClientHandler c : users.all()) c.send(message);
    }

    public void broadcastUserList() {
        broadcastAll(Message.of(MessageType.USER_LIST).putList("users", users.nicknames()));
    }

    // ------------------------------------------------------------------ accessors

    public MessageRouter router() {
        return router;
    }

    public UserManager users() {
        return users;
    }

    public RoomManager rooms() {
        return rooms;
    }

    public GameManager games() {
        return games;
    }

    public StatsStore stats() {
        return stats;
    }
}
