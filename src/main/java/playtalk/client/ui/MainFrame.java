package playtalk.client.ui;

import playtalk.client.game.GameController;
import playtalk.client.net.ServerConnection;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;
import playtalk.common.MessageType;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 메인 화면 (디스코드형 3단 레이아웃).
 * <pre>
 * ┌──────────────────── 상단바 ────────────────────┐
 * │ 방 목록 │        채팅          │  참여자/접속자 │
 * └─────────┴──────────────────────┴────────────────┘
 * </pre>
 * 서버 메시지를 받아 각 패널에 전달하는 역할만 하고, 화면 세부 구현은 패널 클래스에 있다.
 */
public class MainFrame extends JFrame {

    private final ServerConnection connection;
    private final String nickname;
    private final RoomListPanel roomList;
    private final ChatPanel chat;
    private final MemberPanel members;
    private final GameController games;
    private int currentRoomId = -1;
    private int currentRoomMax;
    private boolean waitingStatsDialog;

    public MainFrame(ServerConnection connection, String nickname) {
        super("PlayTalk - " + nickname);
        this.connection = connection;
        this.nickname = nickname;

        roomList = new RoomListPanel(nickname, this::joinRoom, this::createRoom, this::openStats);
        chat = new ChatPanel(nickname, this::sendChat, () -> games().openMultiGame(), this::leaveRoom);
        members = new MemberPanel(nickname, target -> games().challenge(target));
        games = new GameController(this, connection, nickname, chat);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(roomList, BorderLayout.WEST);
        root.add(chat, BorderLayout.CENTER);
        root.add(members, BorderLayout.EAST);
        setContentPane(root);

        registerListeners();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                connection.logout();
                dispose();
                System.exit(0);
            }
        });
        setMinimumSize(new Dimension(960, 600));
        setSize(1200, 760);
        setLocationRelativeTo(null);

        connection.send(Message.of(MessageType.STATS_REQUEST));
    }

    private GameController games() {
        return games;
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.TITLE_BAR);
        bar.setBorder(Theme.padding(8, 16));
        JLabel logo = new JLabel("PlayTalk");
        logo.setFont(Theme.bold(16));
        logo.setForeground(Theme.TITLE_TEXT);
        JLabel sub = new JLabel("  실시간 채팅 & 미니게임");
        sub.setFont(Theme.font(12));
        sub.setForeground(Theme.TITLE_MUTED);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(logo);
        left.add(sub);

        FlatButton stats = new FlatButton("내 전적", FlatButton.Style.DARK);
        stats.setBorder(Theme.padding(5, 12));
        stats.addActionListener(e -> openStats());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(stats);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------ server → UI

    private void registerListeners() {
        connection.on(MessageType.ROOM_LIST, roomList::setRooms);
        connection.on(MessageType.USER_LIST, msg -> members.setOnlineUsers(msg.getList("users")));

        connection.on(MessageType.ROOM_JOINED, msg -> {
            currentRoomId = msg.getInt("roomId", -1);
            currentRoomMax = msg.getInt("max", 5);
            roomList.setCurrentRoom(currentRoomId);
            chat.showRoom(msg.get("title"), currentRoomMax, msg.get("owner"));
            games.onRoomChanged();
        });
        connection.on(MessageType.ROOM_LEFT, msg -> {
            if (msg.getInt("roomId", -1) != currentRoomId) return;
            currentRoomId = -1;
            roomList.setCurrentRoom(-1);
            members.clearMembers();
            chat.showEmpty();
            games.onRoomChanged();
        });
        connection.on(MessageType.ROOM_MEMBERS, msg -> {
            if (msg.getInt("roomId", -1) != currentRoomId) return;
            members.setMembers(msg, currentRoomMax);
            chat.setMemberCount(msg.getTable("members").size(), currentRoomMax);
            chat.setOwner(msg.get("owner"));
        });
        connection.on(MessageType.ROOM_FAIL, msg ->
                JOptionPane.showMessageDialog(this, msg.get("reason"), "채팅방", JOptionPane.WARNING_MESSAGE));

        connection.on(MessageType.CHAT, msg -> {
            if (msg.getInt("roomId", -1) == currentRoomId) {
                chat.appendChat(msg.get("from"), msg.get("text"), msg.get("time"));
            }
        });
        connection.on(MessageType.SYSTEM, msg -> chat.appendSystem(msg.get("text")));
        connection.on(MessageType.ERROR, msg -> {
            if (!games.showErrorInGame(msg.get("text"))) {
                if (currentRoomId >= 0) chat.appendError(msg.get("text"));
                else JOptionPane.showMessageDialog(this, msg.get("text"), "알림", JOptionPane.WARNING_MESSAGE);
            }
        });

        connection.on(MessageType.STATS, msg -> {
            if (!msg.get("nickname").equals(nickname)) return;
            roomList.setStats(msg.getInt("win", 0), msg.getInt("loss", 0), msg.getInt("draw", 0));
            if (waitingStatsDialog) {
                waitingStatsDialog = false;
                new StatsDialog(this, msg).setVisible(true);
            }
        });
        // 게임이 끝나면 전적 갱신
        connection.on(MessageType.GAME_END, msg -> connection.send(Message.of(MessageType.STATS_REQUEST)));

        connection.onDisconnect(() -> {
            JOptionPane.showMessageDialog(this, "서버와의 연결이 끊어졌습니다. 프로그램을 종료합니다.",
                    "연결 끊김", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        });
    }

    // ------------------------------------------------------------------ UI → server

    private void joinRoom(int roomId) {
        if (roomId == currentRoomId) return;
        connection.send(Message.of(MessageType.ROOM_JOIN).put("roomId", roomId));
    }

    private void createRoom() {
        CreateRoomDialog.Result r = new CreateRoomDialog(this).showDialog();
        if (r != null) {
            connection.send(Message.of(MessageType.ROOM_CREATE).put("title", r.title()).put("max", r.max()));
        }
    }

    private void leaveRoom() {
        connection.send(Message.of(MessageType.ROOM_LEAVE));
    }

    private void sendChat(String text) {
        connection.send(Message.of(MessageType.CHAT).put("text", text));
    }

    private void openStats() {
        waitingStatsDialog = true;
        connection.send(Message.of(MessageType.STATS_REQUEST));
    }

    @SuppressWarnings("unused")
    private static JPanel spacer(int w) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, w, 0, 0));
        return p;
    }
}
