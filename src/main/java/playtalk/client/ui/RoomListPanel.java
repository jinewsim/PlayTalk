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

    public RoomListPanel(String nickname, IntConsumer onJoin, Runnable onCreate, Runnable onStats) {
        super(new BorderLayout());
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
                int idx = list.locationToIndex(e.getPoint());
                if (idx >= 0 && list.getCellBounds(idx, idx).contains(e.getPoint())) {
                    onJoin.accept(model.get(idx).id());
                }
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

    public void setCurrentRoom(int roomId) {
        this.currentRoomId = roomId;
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

        RoomRenderer() {
            cell.setBorder(Theme.padding(6, 10));
            name.setFont(Theme.font(13));
            count.setFont(Theme.bold(11));
            cell.add(name, BorderLayout.CENTER);
            cell.add(count, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoomItem> l, RoomItem room, int index,
                                                      boolean selected, boolean focus) {
            boolean current = room.id() == currentRoomId;
            name.setText("# " + room.title());
            name.setFont(current ? Theme.bold(13) : Theme.font(13));
            name.setForeground(current ? Theme.PRIMARY_DARK : Theme.TEXT_MUTED);
            if (room.isFull() && !current) {
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
}
