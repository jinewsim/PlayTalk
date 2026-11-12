package playtalk.client.net;

import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 서버 연결 (클라이언트 쪽).
 *
 * <p>수신 전용 스레드가 메시지를 읽고, 등록된 리스너를 Swing 이벤트 스레드(EDT)에서 호출한다.
 * 그래서 리스너 안에서는 바로 화면을 수정해도 안전하다.
 *
 * <pre>
 * connection.on(MessageType.CHAT, msg -> chatPanel.append(msg));
 * connection.send(Message.of(MessageType.CHAT).put("text", "안녕"));
 * </pre>
 */
public class ServerConnection {

    private final Socket socket = new Socket();
    private final Map<MessageType, List<Consumer<Message>>> listeners = new EnumMap<>(MessageType.class);
    private final List<Runnable> disconnectListeners = new CopyOnWriteArrayList<>();
    private BufferedReader in;
    private BufferedWriter out;
    private volatile boolean closed;

    public void connect(String host, int port) throws IOException {
        socket.connect(new InetSocketAddress(host, port), 3000);
        socket.setTcpNoDelay(true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), Protocol.CHARSET));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), Protocol.CHARSET));
        Thread reader = new Thread(this::readLoop, "PlayTalk-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                Message msg;
                try {
                    msg = Message.decode(line);
                } catch (IllegalArgumentException e) {
                    System.err.println("알 수 없는 메시지: " + line);
                    continue;
                }
                SwingUtilities.invokeLater(() -> dispatch(msg));
            }
        } catch (IOException ignored) {
            // 연결 끊김
        } finally {
            boolean wasOpen = !closed;
            close();
            if (wasOpen) {
                SwingUtilities.invokeLater(() -> disconnectListeners.forEach(Runnable::run));
            }
        }
    }

    private void dispatch(Message msg) {
        List<Consumer<Message>> list;
        synchronized (listeners) {
            list = listeners.get(msg.getType());
        }
        if (list == null) return;
        for (Consumer<Message> l : list) {
            try {
                l.accept(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void send(Message message) {
        if (closed) return;
        synchronized (this) {
            try {
                out.write(message.encode());
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                close();
            }
        }
    }

    public void on(MessageType type, Consumer<Message> listener) {
        synchronized (listeners) {
            listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
        }
    }

    public void off(MessageType type, Consumer<Message> listener) {
        synchronized (listeners) {
            List<Consumer<Message>> list = listeners.get(type);
            if (list != null) list.remove(listener);
        }
    }

    /** 연결이 끊어졌을 때(서버 종료 등) EDT에서 호출된다. 직접 close()한 경우에는 호출되지 않는다. */
    public void onDisconnect(Runnable listener) {
        disconnectListeners.add(listener);
    }

    public void close() {
        if (closed) return;
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    /** 정상 종료: 서버에 알리고 연결을 닫는다. */
    public void logout() {
        send(Message.of(MessageType.LOGOUT));
        close();
    }

    public boolean isClosed() {
        return closed;
    }
}
