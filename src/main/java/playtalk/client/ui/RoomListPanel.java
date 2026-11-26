package playtalk.client.ui;

import playtalk.client.ui.components.Avatar;
import playtalk.client.ui.components.FlatButton;
import playtalk.client.ui.components.RoundedPanel;
import playtalk.common.Message;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/** 왼쪽 사이드바: 내 프로필 + 채팅방 목록 + 방 만들기 (디스코드의 채널 목록 자리). */
public class RoomListPanel extends JPanel {

    public record RoomItem(int id, String title, int current, int max, String owner) {
        boolean isFull() {
            return current >= max;
        }
    }

    private final DefaultListModel<RoomItem> model = new DefaultListModel<>();
    private final JList<RoomItem> list = new JList<>(model);
    private final JLabel countLabel = new JLabel();
    private final JLabel statsLabel = new JLabel("전적 불러오는 중...");
    private int currentRoomId = -1;
    /** 내가 참여 중인 방 id와, 보고 있지 않은 방의 안 읽은 채팅 수 */
    private java.util.Set<Integer> joinedRooms = java.util.Set.of();
    private java.util.Map<Integer, Integer> unread = java.util.Map.of();
    private final String me;
    private IntConsumer onDelete = id -> { };

    public RoomListPanel(String nickname, IntConsumer onJoin, Runnable onCreate, Runnable onStats) {
        super(new BorderLayout());
        this.me = nickname;
        setBackground(Theme.SURFACE_LOW);
        setPreferredSize(new Dimension(250, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_SOFT), Theme.padding(14, 12)));

        // ---- 프로필 카드
        RoundedPanel profile = new RoundedPanel(new BorderLayout(10, 0), Theme.SURFACE, 12).border(Theme.BORDER_SOFT);
        profile.setBorder(Theme.padding(12, 12));
        JLabel avatar = new JLabel(new Avatar(nickname, 40, Theme.SUCCESS));
        JLabel name = new JLabel(nickname);
        name.setFont(Theme.bold(14));
        name.setForeground(Theme.TEXT);
        JLabel online = new JLabel("● 온라인");
        online.setFont(Theme.font(11));
        online.setForeground(Theme.SUCCESS);
        statsLabel.setFont(Theme.font(11));
        statsLabel.setForeground(Theme.PRIMARY_DARK);
        statsLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statsLabel.setToolTipText("내 전적 자세히 보기");
        statsLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onStats.run();
            }
        });
        JPanel info = new JPanel(new BorderLayout(0, 2));
        info.setOpaque(false);
        info.add(name, BorderLayout.NORTH);
        info.add(online, BorderLayout.CENTER);
        info.add(statsLabel, BorderLayout.SOUTH);
        profile.add(avatar, BorderLayout.WEST);
        profile.add(info, BorderLayout.CENTER);

        // ---- 채팅방 목록 헤더
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(16, 4, 6, 0));
        JLabel title = new JLabel("채팅방");
        title.setFont(Theme.bold(12));
        title.setForeground(Theme.TEXT_SUBTLE);
        countLabel.setFont(Theme.font(11));
        countLabel.setForeground(Theme.TEXT_SUBTLE);
        header.add(title, BorderLayout.WEST);
        header.add(countLabel, BorderLayout.EAST);

        list.setCellRenderer(new RoomRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Theme.SURFACE_LOW);
        list.setFixedCellHeight(40);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) return;
                int idx = list.locationToIndex(e.getPoint());
                if (idx >= 0 && list.getCellBounds(idx, idx).contains(e.getPoint())) {
                    onJoin.accept(model.get(idx).id());
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!e.isPopupTrigger() && !javax.swing.SwingUtilities.isRightMouseButton(e)) return;
                int idx = list.locationToIndex(e.getPoint());
                if (idx < 0 || !list.getCellBounds(idx, idx).contains(e.getPoint())) return;
                RoomItem room = model.get(idx);
                if (!room.owner().equals(me)) return; // 방장인 방에서만 메뉴를 띄운다
                javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
                javax.swing.JMenuItem delete = new javax.swing.JMenuItem("채팅방 삭제");
                if (canDelete(room)) {
                    delete.addActionListener(ev -> onDelete.accept(room.id()));
                } else {
                    delete.setEnabled(false);
                    delete.setToolTipText("다른 사람이 있는 방은 삭제할 수 없어요");
                }
                menu.add(delete);
                menu.show(list, e.getX(), e.getY());
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.SURFACE_LOW);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(profile, BorderLayout.NORTH);
        top.add(header, BorderLayout.SOUTH);

        FlatButton create = new FlatButton("+  새 채팅방 만들기", FlatButton.Style.SECONDARY);
        create.addActionListener(e -> onCreate.run());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        bottom.setOpaque(false);
        create.setPreferredSize(new Dimension(224, 38));
        bottom.add(create);

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /** ROOM_LIST 수신 */
    public void setRooms(Message msg) {
        model.clear();
        for (String[] r : msg.getTable("rooms")) {
            model.addElement(new RoomItem(Integer.parseInt(r[0]), r[1],
                    Integer.parseInt(r[2]), Integer.parseInt(r[3]), r[4]));
        }
        countLabel.setText(model.size() + "개");
        list.repaint();
    }

    /** 방장이 방 삭제를 고르면 호출 (roomId) */
    public void setOnDelete(IntConsumer onDelete) {
        this.onDelete = onDelete;
    }

    /** 내가 방장이고, 방에 아무도 없거나 나 혼자 있으면 삭제할 수 있다 */
    private boolean canDelete(RoomItem room) {
        return room.owner().equals(me)
                && (room.current() == 0 || (room.current() == 1 && joinedRooms.contains(room.id())));
    }

    /** 방 목록에서 id로 방 정보를 찾는다 (없으면 null) */
    public RoomItem find(int roomId) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id() == roomId) return model.get(i);
        }
        return null;
    }

    public void setCurrentRoom(int roomId) {
        this.currentRoomId = roomId;
        list.repaint();
    }

    /** 참여 중인 방과 안 읽은 채팅 수를 표시한다 */
    public void setJoined(java.util.Set<Integer> joined, java.util.Map<Integer, Integer> unread) {
        this.joinedRooms = java.util.Set.copyOf(joined);
        this.unread = java.util.Map.copyOf(unread);
        list.repaint();
    }

    /** STATS 수신 */
    public void setStats(int win, int loss, int draw) {
        statsLabel.setText("전적 " + win + "승 " + loss + "패 " + draw + "무  ›");
    }

    private class RoomRenderer implements ListCellRenderer<RoomItem> {
        private final RoundedPanel cell = new RoundedPanel(new BorderLayout(6, 0), Theme.SURFACE_HIGH, 8);
        private final JLabel name = new JLabel();
        private final JLabel count = new JLabel();
        private final Badge badge = new Badge();

        RoomRenderer() {
            cell.setBorder(Theme.padding(6, 10));
            name.setFont(Theme.font(13));
            count.setFont(Theme.bold(11));
            // [방 이름 ........ (배지) 인원] 을 세로 가운데에 맞춰 한 줄에 놓는다
            cell.setLayout(new java.awt.GridBagLayout());
            java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
            gc.gridx = 0;
            gc.weightx = 1;
            gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gc.anchor = java.awt.GridBagConstraints.LINE_START;
            cell.add(name, gc);
            gc = new java.awt.GridBagConstraints();
            gc.gridx = 1;
            gc.insets = new java.awt.Insets(0, 6, 0, 0);
            gc.anchor = java.awt.GridBagConstraints.CENTER;
            cell.add(badge, gc);
            gc = new java.awt.GridBagConstraints();
            gc.gridx = 2;
            gc.insets = new java.awt.Insets(0, 6, 0, 0);
            gc.anchor = java.awt.GridBagConstraints.LINE_END;
            cell.add(count, gc);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoomItem> l, RoomItem room, int index,
                                                      boolean selected, boolean focus) {
            boolean current = room.id() == currentRoomId;
            boolean joined = joinedRooms.contains(room.id());
            int news = unread.getOrDefault(room.id(), 0);
            name.setText("# " + room.title());
            name.setFont(current || joined ? Theme.bold(13) : Theme.font(13));
            name.setForeground(current ? Theme.PRIMARY_DARK : joined ? Theme.TEXT : Theme.TEXT_MUTED);
            badge.setVisible(news > 0); // 안 읽은 메시지가 있으면 인원 숫자 앞에 작은 배지
            if (news > 0) badge.setNumber(news);
            if (room.isFull() && !joined) {
                count.setText("FULL");
                count.setForeground(Theme.ERROR);
            } else {
                count.setText(room.current() + "/" + room.max());
                count.setForeground(current ? Theme.PRIMARY_DARK : Theme.TEXT_SUBTLE);
            }
            cell.setBackground(current ? Theme.SURFACE_HIGH : Theme.SURFACE_LOW);
            cell.setToolTipText("방장: " + room.owner());
            return cell;
        }
    }

    /** 안 읽은 채팅 수 배지 (카카오톡처럼 작은 빨간 알약 + 흰 숫자) */
    private static final class Badge extends JLabel {
        private static final java.awt.Color RED = new java.awt.Color(0xF4503F);
        private static final int HEIGHT = 16;
        private String text = "";

        Badge() {
            setFont(Theme.bold(10));
            setNumber(1);
        }

        void setNumber(int n) {
            text = n > 99 ? "99+" : String.valueOf(n);
            int width = text.length() == 1 ? HEIGHT : text.length() == 2 ? 22 : 28;
            setPreferredSize(new Dimension(width, HEIGHT));
        }

        /** 숫자 글자의 기준선 위치 (방 이름 글자와 같은 높이에 맞추는 데 쓴다) */
        @Override
        public int getBaseline(int width, int height) {
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            return (height - fm.getHeight()) / 2 + fm.getAscent();
        }

        @Override
        public java.awt.Component.BaselineResizeBehavior getBaselineResizeBehavior() {
            return java.awt.Component.BaselineResizeBehavior.CONSTANT_ASCENT;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(RED);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(java.awt.Color.WHITE);
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
