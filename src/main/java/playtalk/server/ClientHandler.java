package playtalk.server;

import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;
import playtalk.server.room.ChatRoom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * 클라이언트 한 명의 연결을 담당하는 스레드.
 *
 * <p>읽기는 이 스레드 하나가 전담하고, 쓰기({@link #send})는 여러 스레드에서 호출될 수 있으므로 동기화한다.
 */
public class ClientHandler implements Runnable, Participant {

    private final Socket socket;
    private final PlayTalkServer server;
    private final BufferedReader in;
    private final BufferedWriter out;

    private volatile String nickname;
    /** 현재 들어가 있는 채팅방 (없으면 null). RoomManager만 변경한다. */
    private volatile ChatRoom room;
    /** 진행 중인 게임 세션 id (없으면 null). GameManager만 변경한다. */
    private volatile String gameSessionId;
    private volatile boolean closed;

    public ClientHandler(Socket socket, PlayTalkServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), Protocol.CHARSET));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), Protocol.CHARSET));
    }

    @Override
    public void run() {
        try {
            String line;
            while (!closed && (line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                Message message;
                try {
                    message = Message.decode(line);
                } catch (IllegalArgumentException e) {
                    sendError("잘못된 메시지 형식입니다.");
                    continue;
                }
                if (message.getType() == MessageType.LOGOUT) break;
                try {
                    server.router().dispatch(this, message);
                } catch (Exception e) {
                    Log.error("%s 처리 중 오류 (%s): %s", message.getType(), nickname, e);
                    e.printStackTrace();
                    sendError("서버에서 요청을 처리하지 못했습니다.");
                }
            }
        } catch (IOException e) {
            // 연결이 비정상적으로 끊어진 경우 (강제 종료, 네트워크 끊김 등)
        } finally {
            close();
            server.onDisconnect(this);
        }
    }

    @Override
    public void send(Message message) {
        if (closed) return;
        synchronized (out) {
            try {
                out.write(message.encode());
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                close();
            }
        }
    }

    public void sendError(String text) {
        send(Message.of(MessageType.ERROR).put("text", text));
    }

    public void sendSystem(String text) {
        send(Message.of(MessageType.SYSTEM).put("text", text));
    }

    public void close() {
        if (closed) return;
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public String nickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isLoggedIn() {
        return nickname != null;
    }

    public ChatRoom getRoom() {
        return room;
    }

    public void setRoom(ChatRoom room) {
        this.room = room;
    }

    public String getGameSessionId() {
        return gameSessionId;
    }

    public void setGameSessionId(String gameSessionId) {
        this.gameSessionId = gameSessionId;
    }

    public boolean isInGame() {
        return gameSessionId != null;
    }

    public String remoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }
}
