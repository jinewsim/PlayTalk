package playtalk.client.ui;

import playtalk.client.ClientMain;
import playtalk.client.game.GameController;
import playtalk.client.net.ServerConnection;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;
import playtalk.common.MessageType;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final MemberPanel members;
    private final FriendPanel friends;
    private final HomePanel home;
    private final GameController games;
    /** 참여 중인 방마다 채팅 화면이 하나씩 있다 (방을 오가도 대화가 그대로 남는다) */
    private final Map<Integer, RoomView> joined = new LinkedHashMap<>();
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = new JPanel(centerCards);
    /** 지금 보고 있는 방 (홈 화면이면 -1) */
    private int viewedRoomId = -1;

    /** 참여 중인 방 하나의 화면과 상태 */
    private static final class RoomView {
        final int id;
        final int max;
        final ChatPanel chat;
        Message members;
        int unread;

        RoomView(int id, int max, ChatPanel chat) {
            this.id = id;
            this.max = max;
            this.chat = chat;
        }
    }
    /** 이 폭보다 좁으면 채팅방 목록을 자동으로 접는다 */
    private static final int NARROW_WIDTH = 1100;
    private boolean wasNarrow;
    /** 이 폭보다 좁으면 오른쪽 참여자·친구 패널도 자동으로 접는다 */
    private static final int VERY_NARROW_WIDTH = 900;
    private boolean wasVeryNarrow;
    private JLabel subtitle;

    private boolean waitingStatsDialog;
    private boolean waitingRanking;
    /** 탈퇴 완료로 서버가 연결을 끊는 중 (연결 끊김 알림을 띄우지 않기 위해) */
    private boolean accountDeleted;

    public MainFrame(ServerConnection connection, String nickname) {
        super("PlayTalk - " + nickname);
        this.connection = connection;
        this.nickname = nickname;

        roomList = new RoomListPanel(nickname, this::joinRoom, this::createRoom, this::openStats);
        friends = new FriendPanel(this::requestFriend, this::replyFriend, this::removeFriend);
        members = new MemberPanel(nickname, target -> games().challenge(target), friends);
        games = new GameController(this, connection, nickname);
        games.setStatsOpener(this::openRoomStats);
        games.setVersusOpener(this::openVersusStats);
        home = new HomePanel(nickname, this::joinRoom, this::createRoom, target -> games().challenge(target), this::openRanking);
        center.add(home, "home");
        friends.setOnChallenge(target -> games().challenge(target));
        // 방 밖(홈 화면)에서는 대결 신청 결과 같은 안내를 홈 화면에 띄운다
        games.setNotifier(text -> {
            RoomView view = joined.get(viewedRoomId);
            if (view != null) view.chat.appendSystem(text);
            else home.showNotice(text);
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(roomList, BorderLayout.WEST);
        root.add(center, BorderLayout.CENTER);
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
        setMinimumSize(new Dimension(760, 560));
        setSize(1200, 760);
        // 창이 좁아지면 채팅방 목록을 자동으로 접고, 넓어지면 다시 편다 (버튼으로 언제든 바꿀 수 있다)
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                boolean narrow = getWidth() < NARROW_WIDTH;
                if (narrow != wasNarrow) {
                    wasNarrow = narrow;
                    setRoomListVisible(!narrow);
                }
                boolean veryNarrow = getWidth() < VERY_NARROW_WIDTH;
                if (veryNarrow != wasVeryNarrow) {
                    wasVeryNarrow = veryNarrow;
                    setMemberPanelVisible(!veryNarrow);
                }
                subtitle.setVisible(getWidth() >= 900);
            }
        });
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
        // 왼쪽 채팅방 목록 접기/펴기
        FlatButton menu = new FlatButton("", FlatButton.Style.DARK);
        menu.setIcon(new MenuIcon(18));
        menu.setBorder(Theme.padding(5, 8));
        menu.setToolTipText("채팅방 목록 접기/펴기");
        menu.addActionListener(e -> setRoomListVisible(!roomList.isVisible()));
        subtitle = sub;
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(menu);
        left.add(Box.createHorizontalStrut(10));
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
        // 오른쪽 참여자·친구 패널 접기/펴기
        FlatButton people = new FlatButton("", FlatButton.Style.DARK);
        people.setIcon(new PanelIcon(18));
        people.setBorder(Theme.padding(5, 8));
        people.setToolTipText("참여자·친구 패널 접기/펴기");
        people.addActionListener(e -> setMemberPanelVisible(!members.isVisible()));
        right.add(people);

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
            int roomId = msg.getInt("roomId", -1);
            if (joined.containsKey(roomId)) {
                showRoom(roomId);
                return;
            }
            games.forgetRoom(roomId); // 이 방의 대기실 상태는 서버가 새로 보내 준다
            int max = msg.getInt("max", 5);
            ChatPanel chat = new ChatPanel(nickname, text -> sendChat(roomId, text),
                    () -> games().openMultiGame(roomId), () -> leaveRoom(roomId), this::showHome);
            chat.showRoom(msg.get("title"), max, msg.get("owner"));
            chat.setOnDelete(() -> deleteRoom(roomId));
            joined.put(roomId, new RoomView(roomId, max, chat));
            center.add(chat, "room" + roomId);

            chat.setLastSeq(msg.getLong("historyUpTo", 0)); // 들어온 시점까지는 읽은 것으로 친다
            int history = msg.getInt("history", 0);
            if (history > 0) {
                long upTo = msg.getLong("historyUpTo", 0);
                chat.showHistoryOffer(history, () -> connection.send(Message.of(MessageType.CHAT_HISTORY_REQUEST)
                        .put("roomId", roomId).put("upTo", upTo)));
            }
            showRoom(roomId); // 새로 들어간 방을 바로 보여준다
        });
        connection.on(MessageType.ROOM_LEFT, msg -> {
            RoomView view = joined.remove(msg.getInt("roomId", -1));
            if (view == null) return;
            games.forgetRoom(view.id);
            center.remove(view.chat);
            if (view.id == viewedRoomId) showHome();
            else refreshRoomBadges();
        });
        connection.on(MessageType.ROOM_MEMBERS, msg -> {
            RoomView view = joined.get(msg.getInt("roomId", -1));
            if (view == null) return;
            view.members = msg;
            view.chat.setMemberCount(msg.getTable("members").size(), view.max);
            view.chat.setOwner(msg.get("owner"));
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String[] r : msg.getTable("members")) names.add(r[0]);
            view.chat.setRoomMembers(names);
            if (view.id == viewedRoomId) members.setMembers(msg, view.max);
        });
        connection.on(MessageType.ROOM_FAIL, msg ->
                Dialogs.alert(this, Dialogs.Kind.WARNING, "채팅방에 들어갈 수 없어요", msg.get("reason")));

        connection.on(MessageType.CHAT, msg -> {
            RoomView view = joined.get(msg.getInt("roomId", -1));
            if (view == null) return;
            view.chat.appendChat(msg.get("from"), msg.get("text"), msg.get("time"), msg.getLong("seq", 0));
            if (view.id == viewedRoomId) {
                if (!msg.get("from").equals(nickname)) sendRead(view);
            } else { // 보고 있지 않은 방의 새 채팅은 방 목록에 개수로 표시
                view.unread++;
                refreshRoomBadges();
            }
        });

        connection.on(MessageType.CHAT_READ, msg -> {
            RoomView view = joined.get(msg.getInt("roomId", -1));
            if (view != null) view.chat.setReads(msg.getTable("reads"));
        });
        connection.on(MessageType.CHAT_HISTORY, msg -> {
            RoomView view = joined.get(msg.getInt("roomId", -1));
            if (view != null) view.chat.showHistory(msg.getTable("messages"));
        });
        connection.on(MessageType.SYSTEM, msg -> {
            // roomId가 있으면 그 방에, 없으면 지금 보고 있는 방에 보여준다
            RoomView view = joined.get(msg.getInt("roomId", viewedRoomId));
            if (view != null) view.chat.appendSystem(msg.get("text"));
        });
        connection.on(MessageType.FRIEND_LIST, msg -> {
            friends.setFriendList(msg);
            home.setFriendList(msg);
        });
        connection.on(MessageType.FRIEND_NOTICE, msg -> friends.showNotice(msg.get("text"), msg.getBoolean("error")));
        connection.on(MessageType.FRIEND_REQUEST, msg ->
                friends.showNotice(msg.get("from") + " 님이 친구 요청을 보냈습니다.", false));

        connection.on(MessageType.ERROR, msg -> {
            if (!games.showErrorInGame(msg.get("text"))) {
                RoomView view = joined.get(viewedRoomId);
                if (view != null) view.chat.appendError(msg.get("text"));
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
        connection.on(MessageType.VERSUS_STATS, msg -> RankingDialog.versus(this, msg, nickname).setVisible(true));
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

    /** 이미 참여 중인 방이면 화면만 전환하고, 아니면 서버에 입장을 요청한다 */
    private void joinRoom(int roomId) {
        if (joined.containsKey(roomId)) {
            showRoom(roomId);
            return;
        }
        // 아직 참여하지 않은 방이면 들어가기 전에 한 번 물어본다
        RoomListPanel.RoomItem room = roomList.find(roomId);
        String heading = room != null ? "'" + room.title() + "' 채팅방에 입장할까요?" : "채팅방에 입장할까요?";
        String detail = room != null ? "현재 " + room.current() + "/" + room.max() + "명 · 방장 " + room.owner() : "";
        if (!Dialogs.confirm(this, Dialogs.Kind.QUESTION, heading, detail, "입장")) return;
        connection.send(Message.of(MessageType.ROOM_JOIN).put("roomId", roomId));
    }

    /** 참여 중인 방의 채팅 화면을 보여준다 (안 읽은 개수는 지운다) */
    private void showRoom(int roomId) {
        RoomView view = joined.get(roomId);
        if (view == null) return;
        viewedRoomId = roomId;
        view.unread = 0;
        centerCards.show(center, "room" + roomId);
        roomList.setCurrentRoom(roomId);
        refreshRoomBadges();
        if (view.members != null) members.setMembers(view.members, view.max);
        else members.clearMembers();
        games.onRoomChanged(roomId);
        sendRead(view); // 방을 열면 거기까지 읽은 것
    }

    /** 이 방의 마지막 메시지까지 읽었다고 서버에 알린다 (읽음 표시) */
    private void sendRead(RoomView view) {
        long seq = view.chat.getLastSeq();
        if (seq > 0) connection.send(Message.of(MessageType.CHAT_READ).put("roomId", view.id).put("seq", seq));
    }

    private void setRoomListVisible(boolean visible) {
        roomList.setVisible(visible);
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private void setMemberPanelVisible(boolean visible) {
        members.setVisible(visible);
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    /** 코드로 그린 오른쪽 패널 아이콘 (네모 안 오른쪽 칸) */
    private static final class PanelIcon implements javax.swing.Icon {
        private final int size;

        PanelIcon(int size) {
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.TITLE_TEXT);
            g2.setStroke(new java.awt.BasicStroke(Math.max(1.6f, size / 10f)));
            int pad = size / 8;
            int w = size - pad * 2, h = size - pad * 3;
            g2.drawRoundRect(x + pad, y + pad + pad / 2, w, h, 4, 4);
            int split = x + pad + w * 6 / 10;
            g2.fillRect(split, y + pad + pad / 2, x + pad + w - split, h);
            g2.dispose();
        }
    }

    /** 코드로 그린 메뉴(햄버거) 아이콘 */
    private static final class MenuIcon implements javax.swing.Icon {
        private final int size;

        MenuIcon(int size) {
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.TITLE_TEXT);
            int h = Math.max(2, size / 9);
            for (int i = 0; i < 3; i++) {
                int yy = y + size / 5 + i * (size * 3 / 10);
                g2.fillRoundRect(x + 1, yy, size - 2, h, h, h);
            }
            g2.dispose();
        }
    }

    /** 방에서 나가지 않고 홈 화면으로 돌아간다 (참여 중인 방은 그대로 남는다) */
    private void showHome() {
        viewedRoomId = -1;
        centerCards.show(center, "home");
        roomList.setCurrentRoom(-1);
        refreshRoomBadges();
        members.clearMembers();
        games.onRoomChanged(-1);
    }

    private void refreshRoomBadges() {
        Map<Integer, Integer> unread = new HashMap<>();
        joined.forEach((id, v) -> unread.put(id, v.unread));
        roomList.setJoined(new HashSet<>(joined.keySet()), unread);
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

    /** 방장이 빈 채팅방을 삭제 (확인 후) */
    private void deleteRoom(int roomId) {
        RoomListPanel.RoomItem room = roomList.find(roomId);
        String name = room != null ? "'" + room.title() + "' 채팅방" : "이 채팅방";
        if (!Dialogs.confirm(this, Dialogs.Kind.DANGER, name + "을 삭제할까요?",
                "채팅방 목록에서 사라지고, 대화 기록도 다시 볼 수 없어요. 되돌릴 수 없어요.", "삭제")) return;
        connection.send(Message.of(MessageType.ROOM_DELETE).put("roomId", roomId));
    }

    private void leaveRoom(int roomId) {
        connection.send(Message.of(MessageType.ROOM_LEAVE).put("roomId", roomId));
    }

    private void sendChat(int roomId, String text) {
        connection.send(Message.of(MessageType.CHAT).put("roomId", roomId).put("text", text));
    }

    /** 게임 창의 "전적 보기": 그 게임을 한 채팅방의 전적만 요청한다 (방 밖에서 한 친구 대결이면 내 전적) */
    private void openRoomStats(int roomId) {
        if (roomId < 0) {
            openStats();
            return;
        }
        connection.send(Message.of(MessageType.ROOM_STATS_REQUEST).put("roomId", roomId));
    }

    /** 1:1 게임 창의 "전적 보기": 그 상대와 한 전적만 요청한다 */
    private void openVersusStats(String opponent) {
        connection.send(Message.of(MessageType.VERSUS_STATS_REQUEST).put("opponent", opponent));
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
