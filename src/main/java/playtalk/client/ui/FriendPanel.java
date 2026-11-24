package playtalk.client.ui;

import playtalk.client.ui.components.Avatar;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;
import playtalk.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 친구 목록 화면 (오른쪽 사이드바의 "친구" 탭).
 * <pre>
 *  [닉네임 입력      ][추가]
 *  받은 요청 1   지은  [수락][거절]
 *  보낸 요청 1   Dana  대기 중 [취소]
 *  온라인 1      민수
 *  오프라인 1    Alex
 * </pre>
 * 서버가 보내는 FRIEND_LIST 전체로 화면을 매번 다시 그린다. 친구를 우클릭하면 삭제할 수 있다.
 */
public class FriendPanel extends JPanel {

    private final Consumer<String> onRequest;
    private final BiConsumer<String, Boolean> onReply;
    private final Consumer<String> onRemove;
    private IntConsumer onReceivedCountChanged = n -> { };

    private final JTextField input = new JTextField();
    private final JLabel noticeLabel = new JLabel(" ");
    private final JPanel content = new JPanel();
    private final Timer noticeTimer = new Timer(5000, e -> noticeLabel.setText(" "));

    public FriendPanel(Consumer<String> onRequest, BiConsumer<String, Boolean> onReply, Consumer<String> onRemove) {
        super(new BorderLayout(0, 6));
        this.onRequest = onRequest;
        this.onReply = onReply;
        this.onRemove = onRemove;
        setOpaque(false);

        // ---- 친구 추가 입력줄
        input.setFont(Theme.font(12));
        input.setForeground(Theme.TEXT);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), Theme.padding(5, 8)));
        input.setToolTipText("친구로 추가할 닉네임을 입력하세요");
        input.addActionListener(e -> submit());
        FlatButton add = small("추가", FlatButton.Style.PRIMARY);
        add.addActionListener(e -> submit());
        JPanel addRow = new JPanel(new BorderLayout(6, 0));
        addRow.setOpaque(false);
        addRow.add(input, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);

        noticeLabel.setFont(Theme.font(11));
        noticeLabel.setForeground(Theme.PRIMARY_DARK);
        noticeLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        noticeTimer.setRepeats(false);

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        north.add(addRow, BorderLayout.NORTH);
        north.add(noticeLabel, BorderLayout.SOUTH);

        // ---- 목록
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.SURFACE_LOW);
        JPanel holder = new JPanel(new BorderLayout()); // 항목이 적을 때 위쪽에 붙도록
        holder.setBackground(Theme.SURFACE_LOW);
        holder.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Theme.SURFACE_LOW);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        setFriendList(List.of(), List.of(), List.of());
    }

    /** 받은 친구 요청 수가 바뀔 때 호출 (탭 제목에 숫자 표시용) */
    public void setOnReceivedCountChanged(IntConsumer listener) {
        this.onReceivedCountChanged = listener;
    }

    /** FRIEND_LIST 수신 */
    public void setFriendList(Message msg) {
        List<Friend> friends = new ArrayList<>();
        for (String[] r : msg.getTable("friends")) {
            friends.add(new Friend(r[0], r.length > 1 && Boolean.parseBoolean(r[1])));
        }
        setFriendList(friends, msg.getList("received"), msg.getList("sent"));
    }

    /** FRIEND_NOTICE / 친구 관련 ERROR 수신: 입력줄 아래에 잠깐 표시 */
    public void showNotice(String text, boolean error) {
        noticeLabel.setForeground(error ? Theme.ERROR : Theme.PRIMARY_DARK);
        noticeLabel.setText(text);
        noticeLabel.setToolTipText(text);
        noticeTimer.restart();
    }

    /** 다른 화면(접속자 목록 우클릭 등)에서 친구 요청을 보낼 때 */
    public void requestFriend(String nickname) {
        onRequest.accept(nickname);
    }

    private record Friend(String nickname, boolean online) {
    }

    private void submit() {
        String target = input.getText().trim();
        String error = Protocol.validateNickname(target);
        if (error != null) {
            showNotice(error, true);
            return;
        }
        onRequest.accept(target);
        input.setText("");
    }

    private void setFriendList(List<Friend> friends, List<String> received, List<String> sent) {
        content.removeAll();

        if (!received.isEmpty()) {
            content.add(section("받은 요청  " + received.size(), Theme.PRIMARY_DARK));
            for (String from : received) {
                FlatButton accept = small("수락", FlatButton.Style.PRIMARY);
                accept.addActionListener(e -> onReply.accept(from, true));
                FlatButton decline = small("거절", FlatButton.Style.GHOST);
                decline.addActionListener(e -> onReply.accept(from, false));
                content.add(row(from, null, "친구 요청", Theme.PRIMARY_DARK, accept, decline));
            }
        }
        if (!sent.isEmpty()) {
            content.add(section("보낸 요청  " + sent.size(), Theme.TEXT_SUBTLE));
            for (String to : sent) {
                FlatButton cancel = small("취소", FlatButton.Style.GHOST);
                cancel.addActionListener(e -> onRemove.accept(to));
                content.add(row(to, null, "수락 대기 중", Theme.TEXT_SUBTLE, cancel));
            }
        }

        List<Friend> online = friends.stream().filter(Friend::online).toList();
        List<Friend> offline = friends.stream().filter(f -> !f.online()).toList();
        content.add(section("온라인  " + online.size(), Theme.SUCCESS));
        for (Friend f : online) content.add(friendRow(f));
        content.add(section("오프라인  " + offline.size(), Theme.TEXT_SUBTLE));
        for (Friend f : offline) content.add(friendRow(f));

        if (friends.isEmpty() && received.isEmpty() && sent.isEmpty()) {
            JLabel empty = new JLabel("<html>아직 친구가 없어요.<br>닉네임을 입력해 친구를 추가해 보세요.</html>");
            empty.setFont(Theme.font(11));
            empty.setForeground(Theme.TEXT_SUBTLE);
            empty.setBorder(BorderFactory.createEmptyBorder(8, 6, 0, 0));
            content.add(leftAligned(empty));
        }

        content.revalidate();
        content.repaint();
        onReceivedCountChanged.accept(received.size());
    }

    private JComponent friendRow(Friend f) {
        JComponent row = row(f.nickname(), f.online() ? Theme.SUCCESS : Theme.BORDER,
                f.online() ? "온라인" : "오프라인", f.online() ? Theme.SUCCESS : Theme.TEXT_SUBTLE);
        row.setToolTipText("우클릭: 친구 삭제");
        JPopupMenu menu = new JPopupMenu();
        JMenuItem remove = new JMenuItem("친구 삭제");
        remove.addActionListener(e -> onRemove.accept(f.nickname()));
        menu.add(remove);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) menu.show(row, e.getX(), e.getY());
            }
        });
        return row;
    }

    /** 아바타 + 닉네임/상태 + 오른쪽 버튼들로 된 한 줄 */
    private static JComponent row(String nickname, Color dot, String status, Color statusColor, FlatButton... buttons) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(Theme.padding(4, 4));

        JLabel name = new JLabel(nickname);
        name.setFont(Theme.bold(13));
        name.setForeground(Theme.TEXT);
        JLabel state = new JLabel(status);
        state.setFont(Theme.font(11));
        state.setForeground(statusColor);
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(name, BorderLayout.NORTH);
        text.add(state, BorderLayout.SOUTH);

        row.add(new JLabel(new Avatar(nickname, 30, dot)), BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        if (buttons.length > 0) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
            actions.setOpaque(false);
            for (FlatButton b : buttons) actions.add(b);
            row.add(actions, BorderLayout.EAST);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static JComponent section(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(11));
        l.setForeground(color);
        l.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 0));
        return leftAligned(l);
    }

    private static JComponent leftAligned(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.WEST);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        p.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        return p;
    }

    private static FlatButton small(String text, FlatButton.Style style) {
        FlatButton b = new FlatButton(text, style);
        b.setFont(Theme.bold(11));
        b.setBorder(Theme.padding(4, 8));
        return b;
    }
}
