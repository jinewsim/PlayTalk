package playtalk.client.ui;

import playtalk.client.ClientMain;
import playtalk.client.game.GameController;
import playtalk.client.net.ServerConnection;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;
import playtalk.common.MessageType;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
    private final FriendPanel friends;
    private final HomePanel home;
    private final GameController games;
    private int currentRoomId = -1;
    private int currentRoomMax;
    private boolean waitingStatsDialog;
    private boolean waitingRanking;
    /** 탈퇴 완료로 서버가 연결을 끊는 중 (연결 끊김 알림을 띄우지 않기 위해) */
    private boolean accountDeleted;

    public MainFrame(ServerConnection connection, String nickname) {
        super("PlayTalk - " + nickname);
        this.connection = connection;
        this.nickname = nickname;

        roomList = new RoomListPanel(nickname, this::joinRoom, this::createRoom, this::openStats);
        chat = new ChatPanel(nickname, this::sendChat, () -> games().openMultiGame(), this::leaveRoom);
        friends = new FriendPanel(this::requestFriend, this::replyFriend, this::removeFriend);
        members = new MemberPanel(nickname, target -> games().challenge(target), friends);
        games = new GameController(this, connection, nickname, chat);
        games.setStatsOpener(this::openRoomStats);
        home = new HomePanel(nickname, this::joinRoom, this::createRoom, target -> games().challenge(target), this::openRanking);
        chat.setEmptyView(home);
        friends.setOnChallenge(target -> games().challenge(target));
        // 방 밖(홈 화면)에서는 대결 신청 결과 같은 안내를 홈 화면에 띄운다
        games.setNotifier(text -> {
            if (currentRoomId >= 0) chat.appendSystem(text);
            else home.showNotice(text);
        });

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
        connection.send(Message.of(MessageType.RANKING_REQUEST)); // 홈 화면 랭킹 TOP 3
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
        FlatButton ranking = new FlatButton("랭킹", FlatButton.Style.DARK);
        ranking.setBorder(Theme.padding(5, 12));
        ranking.addActionListener(e -> openRanking());
        FlatButton account = new FlatButton("계정", FlatButton.Style.DARK);
        account.setBorder(Theme.padding(5, 12));
        JPopupMenu accountMenu = new JPopupMenu();
        JMenuItem logout = new JMenuItem("로그아웃");
        logout.addActionListener(e -> logout());
        JMenuItem withdraw = new JMenuItem("회원 탈퇴...");
        withdraw.setForeground(Theme.ERROR);
        logout.setPreferredSize(new Dimension(150, logout.getPreferredSize().height));
        withdraw.addActionListener(e -> withdraw());
        accountMenu.add(logout);
        accountMenu.addSeparator();
        accountMenu.add(withdraw);
        account.addActionListener(e -> {
            // 버튼 아래, 오른쪽 끝을 맞춰서 연다
            accountMenu.show(account, account.getWidth() - accountMenu.getPreferredSize().width, account.getHeight() + 6);
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(ranking);
        right.add(stats);
        right.add(account);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------ server → UI

    private void registerListeners() {
        connection.on(MessageType.ROOM_LIST, msg -> {
            roomList.setRooms(msg);
            home.setRoomList(msg);
        });
        connection.on(MessageType.USER_LIST, msg -> members.setOnlineUsers(msg.getList("users")));

        connection.on(MessageType.ROOM_JOINED, msg -> {
            currentRoomId = msg.getInt("roomId", -1);
            currentRoomMax = msg.getInt("max", 5);
            roomList.setCurrentRoom(currentRoomId);
            chat.showRoom(msg.get("title"), currentRoomMax, msg.get("owner"));

            int history = msg.getInt("history", 0);
            if (history > 0) {
                int roomId = currentRoomId;
                long upTo = msg.getLong("historyUpTo", 0);
                chat.showHistoryOffer(history, () -> connection.send(Message.of(MessageType.CHAT_HISTORY_REQUEST)
                        .put("roomId", roomId).put("upTo", upTo)));
            }

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
                Dialogs.alert(this, Dialogs.Kind.WARNING, "채팅방에 들어갈 수 없어요", msg.get("reason")));

        connection.on(MessageType.CHAT, msg -> {
            if (msg.getInt("roomId", -1) == currentRoomId) {
                chat.appendChat(msg.get("from"), msg.get("text"), msg.get("time"));
            }
        });

        connection.on(MessageType.CHAT_HISTORY, msg -> {
            if (msg.getInt("roomId", -1) == currentRoomId) chat.showHistory(msg.getTable("messages"));
        });
        connection.on(MessageType.SYSTEM, msg -> chat.appendSystem(msg.get("text")));
        connection.on(MessageType.FRIEND_LIST, msg -> {
            friends.setFriendList(msg);
            home.setFriendList(msg);
        });
        connection.on(MessageType.FRIEND_NOTICE, msg -> friends.showNotice(msg.get("text"), msg.getBoolean("error")));
        connection.on(MessageType.FRIEND_REQUEST, msg ->
                friends.showNotice(msg.get("from") + " 님이 친구 요청을 보냈습니다.", false));

        connection.on(MessageType.ERROR, msg -> {
            if (!games.showErrorInGame(msg.get("text"))) {
                if (currentRoomId >= 0) chat.appendError(msg.get("text"));
                else Dialogs.alert(this, Dialogs.Kind.WARNING, "요청을 처리할 수 없어요", msg.get("text"));
            }
        });

        connection.on(MessageType.STATS, msg -> {
            if (!msg.get("nickname").equals(nickname)) return;
            roomList.setStats(msg.getInt("win", 0), msg.getInt("loss", 0), msg.getInt("draw", 0));
            home.setStats(msg.getInt("win", 0), msg.getInt("loss", 0), msg.getInt("draw", 0));
            if (waitingStatsDialog) {
                waitingStatsDialog = false;
                new StatsDialog(this, msg).setVisible(true);
            }
        });
        connection.on(MessageType.RANKING, msg -> {
            home.setRankingList(msg);
            if (!waitingRanking) return;
            waitingRanking = false;
            RankingDialog.global(this, msg, nickname).setVisible(true);
        });
        connection.on(MessageType.ROOM_STATS, msg -> RankingDialog.room(this, msg, nickname).setVisible(true));
        // 게임이 끝나면 전적 갱신
        connection.on(MessageType.GAME_END, msg -> {
            connection.send(Message.of(MessageType.STATS_REQUEST));
            connection.send(Message.of(MessageType.RANKING_REQUEST));
        });

        connection.on(MessageType.ACCOUNT_DELETED, msg -> {
            accountDeleted = true;
            connection.close();
            dispose();
            Dialogs.alert(null, Dialogs.Kind.INFO, "회원 탈퇴가 완료되었어요", "그동안 PlayTalk를 이용해 주셔서 감사합니다.");
            ClientMain.showLogin();
        });
        connection.on(MessageType.ACCOUNT_DELETE_FAIL, msg ->
                Dialogs.alert(this, Dialogs.Kind.WARNING, "탈퇴하지 못했어요", msg.get("reason")));

        connection.onDisconnect(() -> {
            if (accountDeleted) return;
            Dialogs.alert(this, Dialogs.Kind.DANGER, "서버와의 연결이 끊어졌어요", "프로그램을 종료합니다.");
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

    // ---- 친구
    private void requestFriend(String target) {
        connection.send(Message.of(MessageType.FRIEND_REQUEST).put("target", target));
    }

    private void replyFriend(String from, boolean accept) {
        connection.send(Message.of(MessageType.FRIEND_REPLY).put("from", from).put("accept", accept));
    }

    private void removeFriend(String target) {
        connection.send(Message.of(MessageType.FRIEND_REMOVE).put("target", target));
    }

    private void leaveRoom() {
        connection.send(Message.of(MessageType.ROOM_LEAVE));
    }

    private void sendChat(String text) {
        connection.send(Message.of(MessageType.CHAT).put("text", text));
    }

    /** 게임 창의 "전적 보기": 그 게임을 한 채팅방의 전적만 요청한다 (방 밖에서 한 친구 대결이면 내 전적) */
    private void openRoomStats(int roomId) {
        if (roomId < 0) {
            openStats();
            return;
        }
        connection.send(Message.of(MessageType.ROOM_STATS_REQUEST).put("roomId", roomId));
    }

    // ---- 계정
    private void logout() {
        if (!Dialogs.confirm(this, Dialogs.Kind.QUESTION, "로그아웃할까요?",
                "진행 중인 게임이 있으면 기권으로 처리돼요.", "로그아웃")) return;
        connection.logout();
        dispose();
        ClientMain.showLogin();
    }

    /** 회원 탈퇴: 비밀번호를 한 번 더 입력받아 서버에 요청 (결과는 ACCOUNT_DELETED / ACCOUNT_DELETE_FAIL) */
    private void withdraw() {
        String pw = Dialogs.password(this, Dialogs.Kind.DANGER, "정말 탈퇴할까요?",
                nickname + " 계정과 친구 목록, 전적, 랭킹 기록이 모두 삭제되며 되돌릴 수 없어요.\n계속하려면 비밀번호를 입력하세요.",
                "탈퇴하기");
        if (pw != null) connection.send(Message.of(MessageType.ACCOUNT_DELETE).put("password", pw));
    }

    private void openRanking() {
        waitingRanking = true;
        connection.send(Message.of(MessageType.RANKING_REQUEST));
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
