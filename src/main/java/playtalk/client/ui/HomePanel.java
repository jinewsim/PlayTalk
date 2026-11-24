package playtalk.client.ui;

import playtalk.client.ui.components.Avatar;
import playtalk.client.ui.components.FlatButton;
import playtalk.client.ui.components.RoundedPanel;
import playtalk.common.Message;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 홈 화면: 채팅방에 들어가기 전 가운데 영역.
 * <pre>
 *  안녕하세요, 유진님!            전적 5승 3패 1무 · 승률 56%
 *  온라인 친구   [민수 (대결)] [Dana (대결)]
 *  열려 있는 채팅방  [#게임방 3/5 (입장)] [#수다방 2/4 (입장)] [+ 새 채팅방]
 *  랭킹 TOP 3   1. Alex 80%  2. 유진 62%  3. 민수 55%
 * </pre>
 * 화면에 보이는 정보는 이미 받고 있는 ROOM_LIST, FRIEND_LIST, STATS, RANKING 메시지로 채운다.
 */
public class HomePanel extends JPanel {

    /** 랭킹에 오르는 최소 판수 (RankingDialog와 같음) */
    private static final int MIN_GAMES_FOR_RATE = 3;

    private final String me;
    private final IntConsumer onJoinRoom;
    private final Runnable onCreateRoom;
    private final Consumer<String> onChallenge;
    private final Runnable onOpenRanking;

    private final JLabel statsLabel = new JLabel(" ");
    private final JLabel noticeLabel = new JLabel(" ");
    private final JPanel friendCards = grid(3);
    private final JPanel roomCards = grid(2);
    private final JPanel rankingRows = new JPanel();
    private final JLabel friendTitle = sectionTitle("온라인 친구");
    private final JLabel roomTitle = sectionTitle("열려 있는 채팅방");
    private final Timer noticeTimer = new Timer(6000, e -> noticeLabel.setText(" "));

    public HomePanel(String me, IntConsumer onJoinRoom, Runnable onCreateRoom,
                     Consumer<String> onChallenge, Runnable onOpenRanking) {
        super(new BorderLayout());
        this.me = me;
        this.onJoinRoom = onJoinRoom;
        this.onCreateRoom = onCreateRoom;
        this.onChallenge = onChallenge;
        this.onOpenRanking = onOpenRanking;
        setBackground(Theme.SURFACE);

        Column body = new Column();
        body.setBorder(Theme.padding(28, 32));

        // ---- 인사 + 전적
        JLabel hello = new JLabel("안녕하세요, " + me + "님!");
        hello.setFont(Theme.bold(22));
        hello.setForeground(Theme.TEXT);
        statsLabel.setFont(Theme.font(13));
        statsLabel.setForeground(Theme.TEXT_SUBTLE);
        noticeLabel.setFont(Theme.font(12));
        noticeLabel.setForeground(Theme.PRIMARY_DARK);
        noticeTimer.setRepeats(false);
        body.add(left(hello));
        body.add(Box.createVerticalStrut(4));
        body.add(left(statsLabel));
        body.add(Box.createVerticalStrut(6));
        body.add(left(noticeLabel));
        body.add(Box.createVerticalStrut(18));

        // ---- 온라인 친구
        body.add(left(friendTitle));
        body.add(Box.createVerticalStrut(8));
        body.add(friendCards);
        body.add(Box.createVerticalStrut(24));

        // ---- 채팅방
        body.add(left(roomTitle));
        body.add(Box.createVerticalStrut(8));
        body.add(roomCards);
        body.add(Box.createVerticalStrut(24));

        // ---- 랭킹
        FlatButton allRanking = new FlatButton("전체 보기", FlatButton.Style.GHOST);
        allRanking.setFont(Theme.bold(11));
        allRanking.setBorder(Theme.padding(2, 6));
        allRanking.addActionListener(e -> onOpenRanking.run());
        JPanel rankHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rankHeader.setOpaque(false);
        rankHeader.add(sectionTitle("랭킹 TOP 3"));
        rankHeader.add(Box.createHorizontalStrut(8));
        rankHeader.add(allRanking);
        rankingRows.setLayout(new GridLayout(1, 3, 10, 0));
        rankingRows.setOpaque(false);
        rankingRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(left(rankHeader));
        body.add(Box.createVerticalStrut(8));
        body.add(rankingRows);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        setStats(0, 0, 0);
        setFriends(List.of());
        setRooms(List.of());
        setRanking(List.of());
    }

    // ------------------------------------------------------------------ 데이터 반영

    public void setStats(int win, int loss, int draw) {
        int total = win + loss + draw;
        String rate = total == 0 ? "아직 게임 기록이 없어요" : "승률 " + Math.round(win * 100.0 / total) + "%";
        statsLabel.setText("전적 " + win + "승 " + loss + "패 " + draw + "무 · " + rate);
    }

    /** FRIEND_LIST 수신 */
    public void setFriendList(Message msg) {
        List<String> online = new ArrayList<>();
        for (String[] r : msg.getTable("friends")) {
            if (r.length > 1 && Boolean.parseBoolean(r[1])) online.add(r[0]);
        }
        setFriends(online);
    }

    /** ROOM_LIST 수신 */
    public void setRoomList(Message msg) {
        setRooms(msg.getTable("rooms"));
    }

    /** RANKING 수신 */
    public void setRankingList(Message msg) {
        setRanking(msg.getTable("users"));
    }

    /** 방 밖에서 받은 게임 초대 결과 등 안내 */
    public void showNotice(String text) {
        noticeLabel.setText(text);
        noticeTimer.restart();
    }

    // ------------------------------------------------------------------ 섹션 그리기

    private void setFriends(List<String> online) {
        friendTitle.setText("온라인 친구  " + online.size());
        friendCards.removeAll();
        friendCards.setLayout(online.isEmpty() ? new BorderLayout() : new GridLayout(0, 3, 10, 10));
        for (String f : online) {
            FlatButton battle = small("1:1 대결", FlatButton.Style.PRIMARY);
            battle.addActionListener(e -> onChallenge.accept(f));
            friendCards.add(card(new Avatar(f, 36, Theme.SUCCESS), f, "온라인", Theme.SUCCESS, battle));
        }
        if (online.isEmpty()) {
            friendCards.add(hint("접속 중인 친구가 없어요. 오른쪽 친구 탭에서 친구를 추가해 보세요."));
        }
        refresh(friendCards);
    }

    private void setRooms(List<String[]> rooms) {
        roomTitle.setText("열려 있는 채팅방  " + rooms.size());
        roomCards.removeAll();
        for (String[] r : rooms) {
            int id = Integer.parseInt(r[0]);
            int current = Integer.parseInt(r[2]);
            int max = Integer.parseInt(r[3]);
            boolean full = current >= max;
            FlatButton join = small(full ? "가득 참" : "입장", full ? FlatButton.Style.GHOST : FlatButton.Style.SECONDARY);
            join.setEnabled(!full);
            join.addActionListener(e -> onJoinRoom.accept(id));
            JLabel icon = new JLabel("#");
            icon.setFont(Theme.bold(20));
            icon.setForeground(Theme.PRIMARY);
            icon.setPreferredSize(new Dimension(36, 36));
            icon.setHorizontalAlignment(JLabel.CENTER);
            roomCards.add(card(icon, r[1], current + "/" + max + "명 · 방장 " + r[4], Theme.TEXT_SUBTLE, join));
        }
        FlatButton create = small("+ 새 채팅방", FlatButton.Style.SOFT);
        create.addActionListener(e -> onCreateRoom.run());
        JPanel createCard = new RoundedPanel(new BorderLayout(), Theme.SURFACE_LOW, 14);
        createCard.setBorder(Theme.padding(12, 12));
        createCard.add(create, BorderLayout.CENTER);
        roomCards.add(createCard);
        refresh(roomCards);
    }

    private record Rank(String nickname, int win, int total) {
        double rate() {
            return total == 0 ? 0 : (double) win / total;
        }
    }

    private void setRanking(List<String[]> rows) {
        List<Rank> ranks = new ArrayList<>();
        for (String[] r : rows) {
            int w = Integer.parseInt(r[1]);
            int total = w + Integer.parseInt(r[2]) + Integer.parseInt(r[3]);
            if (total >= MIN_GAMES_FOR_RATE) ranks.add(new Rank(r[0], w, total));
        }
        ranks.sort(Comparator.comparingDouble(Rank::rate).reversed().thenComparing(Rank::total, Comparator.reverseOrder()));

        rankingRows.removeAll();
        if (ranks.isEmpty()) {
            rankingRows.setLayout(new BorderLayout());
            rankingRows.add(hint(MIN_GAMES_FOR_RATE + "판 이상 플레이하면 랭킹에 올라요."));
            refresh(rankingRows);
            return;
        }
        rankingRows.setLayout(new GridLayout(1, 3, 10, 0));
        Color[] medal = {new Color(0xD4A017), new Color(0x8A94A6), new Color(0xB87333)};
        for (int i = 0; i < 3; i++) {
            if (i < ranks.size()) {
                Rank r = ranks.get(i);
                JLabel place = new JLabel(String.valueOf(i + 1));
                place.setFont(Theme.bold(20));
                place.setForeground(medal[i]);
                place.setPreferredSize(new Dimension(28, 36));
                place.setHorizontalAlignment(JLabel.CENTER);
                String name = r.nickname().equals(me) ? r.nickname() + " (나)" : r.nickname();
                rankingRows.add(card(place, name, "승률 " + Math.round(r.rate() * 100) + "% · " + r.total() + "판",
                        Theme.TEXT_SUBTLE));
            } else {
                rankingRows.add(blank());
            }
        }
        refresh(rankingRows);
    }

    // ------------------------------------------------------------------ 부품

    /** 왼쪽 아이콘 + 제목/설명 + 오른쪽 버튼으로 된 둥근 카드 */
    private static JComponent card(Object icon, String title, String sub, Color subColor, FlatButton... buttons) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(10, 0), Theme.SURFACE_LOW, 14);
        card.setBorder(Theme.padding(12, 12));
        JLabel iconLabel = icon instanceof JLabel l ? l : new JLabel((javax.swing.Icon) icon);
        card.add(iconLabel, BorderLayout.WEST);

        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(14));
        t.setForeground(Theme.TEXT);
        JLabel s = new JLabel(sub);
        s.setFont(Theme.font(11));
        s.setForeground(subColor);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(t);
        text.add(s);
        card.add(text, BorderLayout.CENTER);

        if (buttons.length > 0) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
            actions.setOpaque(false);
            for (FlatButton b : buttons) actions.add(b);
            card.add(actions, BorderLayout.EAST);
        }
        return card;
    }

    private static JPanel grid(int columns) {
        JPanel p = new JPanel(new GridLayout(0, columns, 10, 10));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(14));
        l.setForeground(Theme.TEXT_MUTED);
        return l;
    }

    private static JComponent hint(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(12));
        l.setForeground(Theme.TEXT_SUBTLE);
        l.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 0));
        return l;
    }

    private static JComponent blank() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static FlatButton small(String text, FlatButton.Style style) {
        FlatButton b = new FlatButton(text, style);
        b.setFont(Theme.bold(12));
        b.setBorder(Theme.padding(5, 10));
        return b;
    }

    private static void refresh(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        c.revalidate();
        c.repaint();
    }

    /** 스크롤 안에서 가로 폭은 화면에 맞추고 세로만 늘어나는 세로 묶음 */
    private static class Column extends JPanel implements Scrollable {
        Column() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Theme.SURFACE);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
