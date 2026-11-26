package playtalk.client.ui;

import playtalk.client.ui.components.Avatar;
import playtalk.client.ui.components.FlatButton;
import playtalk.client.ui.components.RoundedPanel;
import playtalk.common.Emoticons;
import playtalk.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/** 가운데 영역: 채팅방 헤더 + 메시지 목록 + 입력창. */
public class ChatPanel extends JPanel {

    private final CardLayout cards = new CardLayout();
    private final JLabel titleLabel = new JLabel();
    private final JLabel countBadge = new JLabel();
    private final JLabel ownerLabel = new JLabel();
    /** 방장이고 나 혼자 남았을 때만 보이는 삭제 버튼 */
    private final FlatButton deleteButton = new FlatButton("방 삭제", FlatButton.Style.GHOST);
    private Runnable onDelete = () -> { };
    private String owner = "";
    private int memberCount;
    private final MessageList messages = new MessageList();
    private final JScrollPane scroll = new JScrollPane(messages);
    private final EmoticonInput input = new EmoticonInput(2);
    private final String myNickname;
    private String lastSender;
    /** 읽음 표시용: 마지막 채팅 번호, 그게 내 메시지인지, 방 사람들, 각자 읽은 위치 */
    private long lastSeq;
    private boolean lastChatMine;
    private java.util.List<String> roomMembers = java.util.List.of();
    private java.util.Map<String, Long> reads = new java.util.HashMap<>();
    private JComponent receiptRow;
    /** 마지막 채팅 메시지 줄 (읽음 표시는 항상 이 줄 바로 아래에 붙는다) */
    private JComponent lastChatRow;
    private JComponent lastAddedRow;
    /** 이전 대화 불러오기 안내 줄 (없으면 null) */
    private JComponent historyBanner;
    /** 0 이상이면 새 줄을 맨 아래가 아니라 이 위치에 끼워 넣는다 (이전 대화를 위쪽에 채울 때) */
    private int insertAt = -1;

    public ChatPanel(String myNickname, Consumer<String> onSend, Runnable onMultiGame, Runnable onLeave, Runnable onHome) {
        this.myNickname = myNickname;
        setLayout(cards);
        add(buildEmpty(), "empty");
        add(buildRoom(onSend, onMultiGame, onLeave, onHome), "room");
        cards.show(this, "empty");
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildEmpty() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.SURFACE);
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel big = new JLabel("PlayTalk에 오신 것을 환영합니다!");
        big.setFont(Theme.bold(20));
        big.setForeground(Theme.TEXT);
        JLabel small = new JLabel("왼쪽에서 채팅방을 고르거나 새 채팅방을 만들어 보세요.");
        small.setFont(Theme.font(13));
        small.setForeground(Theme.TEXT_SUBTLE);
        big.setAlignmentX(Component.CENTER_ALIGNMENT);
        small.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(big);
        box.add(Box.createVerticalStrut(8));
        box.add(small);
        p.add(box);
        return p;
    }

    private JComponent buildRoom(Consumer<String> onSend, Runnable onMultiGame, Runnable onLeave, Runnable onHome) {
        JPanel room = new JPanel(new BorderLayout());
        room.setBackground(Theme.SURFACE);

        // ---- header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT), Theme.padding(12, 18)));
        titleLabel.setFont(Theme.bold(17));
        titleLabel.setForeground(Theme.TEXT);
        countBadge.setFont(Theme.bold(11));
        countBadge.setForeground(Theme.PRIMARY_DARK);
        countBadge.setOpaque(true);
        countBadge.setBackground(Theme.SURFACE_HIGH);
        countBadge.setBorder(Theme.padding(2, 8));
        ownerLabel.setFont(Theme.font(12));
        ownerLabel.setForeground(Theme.TEXT_SUBTLE);
        // 창이 좁아지면 방 제목이 "..."으로 줄어들고, 버튼과 겹치지 않는다
        JPanel titleRow = new JPanel();
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        titleRow.setOpaque(false);
        titleLabel.setMinimumSize(new Dimension(24, 0));
        ownerLabel.setMinimumSize(new Dimension(0, 0));
        FlatButton home = new FlatButton("", FlatButton.Style.GHOST);
        home.setIcon(new HomeIcon(20));
        home.setBorder(Theme.padding(4, 6));
        home.setToolTipText("홈 화면으로 (이 방에는 그대로 남아 있어요)");
        home.addActionListener(e -> onHome.run());
        titleRow.add(titleLabel);
        titleRow.add(Box.createHorizontalStrut(8));
        titleRow.add(countBadge);
        titleRow.add(Box.createHorizontalStrut(8));
        titleRow.add(ownerLabel);
        titleRow.add(Box.createHorizontalGlue());
        for (Component c : titleRow.getComponents()) {
            if (c instanceof JComponent jc) jc.setAlignmentY(Component.CENTER_ALIGNMENT);
        }
        JPanel homeWrap = new JPanel(new BorderLayout());
        homeWrap.setOpaque(false);
        homeWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        homeWrap.add(home, BorderLayout.CENTER);

        FlatButton multi = new FlatButton("다인용 게임");
        multi.addActionListener(e -> onMultiGame.run());
        FlatButton leave = new FlatButton("나가기", FlatButton.Style.GHOST);
        leave.addActionListener(e -> onLeave.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        deleteButton.setForeground(Theme.ERROR);
        deleteButton.setToolTipText("아무도 없는 방을 목록에서 지워요");
        deleteButton.setVisible(false);
        deleteButton.addActionListener(e -> onDelete.run());
        actions.add(multi);
        actions.add(deleteButton);
        actions.add(leave);
        header.add(homeWrap, BorderLayout.WEST);
        header.add(titleRow, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);

        // ---- messages
        scroll.setBorder(null);
        // 창 폭이 바뀌면 말풍선·안내 문구의 줄바꿈 폭을 다시 계산한다
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = scroll.getViewport().getWidth();
                if (w == lastWidth) return;
                lastWidth = w;
                invalidateTree(messages);
                messages.revalidate();
                messages.repaint();
            }
        });
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // ---- input
        input.setFont(Theme.font(14));
        input.setForeground(Theme.TEXT);
        input.setOpaque(false);
        input.setBorder(Theme.padding(6, 4));
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    submit(onSend);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    input.replaceSelection("\n");
                    e.consume();
                }
            }
        });

        JButton emoji = new FlatButton("", FlatButton.Style.GHOST);
        ImageIcon smile = EmoticonIcons.get("smile", 22);
        if (smile != null) emoji.setIcon(smile); else emoji.setText(":)");
        emoji.setToolTipText("이모티콘");
        emoji.setBorder(Theme.padding(6, 6));
        emoji.addActionListener(e -> showEmoticonPicker(emoji));

        FlatButton send = new FlatButton("전송");
        send.addActionListener(e -> submit(onSend));

        RoundedPanel inputBox = new RoundedPanel(new BorderLayout(6, 0), Theme.SURFACE_LOW, 12).border(Theme.BORDER_SOFT);
        inputBox.setBorder(Theme.padding(6, 8));
        JScrollPane inputScroll = new JScrollPane(input);
        inputScroll.setBorder(null);
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(false);
        inputBox.add(emoji, BorderLayout.WEST);
        inputBox.add(inputScroll, BorderLayout.CENTER);
        JPanel sendWrap = new JPanel(new BorderLayout());
        sendWrap.setOpaque(false);
        sendWrap.add(send, BorderLayout.SOUTH);
        inputBox.add(sendWrap, BorderLayout.EAST);

        JLabel hint = new JLabel("Enter 전송 · Shift+Enter 줄바꿈");
        hint.setFont(Theme.font(11));
        hint.setForeground(Theme.TEXT_SUBTLE);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBackground(Theme.SURFACE);
        bottom.setBorder(Theme.padding(10, 16));
        bottom.add(inputBox, BorderLayout.CENTER);
        bottom.add(hint, BorderLayout.SOUTH);

        room.add(header, BorderLayout.NORTH);
        room.add(scroll, BorderLayout.CENTER);
        room.add(bottom, BorderLayout.SOUTH);
        return room;
    }

    private void submit(Consumer<String> onSend) {
        String text = input.getMessageText().strip();
        if (text.isEmpty()) return;
        if (text.length() > Protocol.CHAT_MAX_LENGTH) {
            appendError("메시지는 " + Protocol.CHAT_MAX_LENGTH + "자까지 보낼 수 있습니다.");
            return;
        }
        onSend.accept(text);
        input.setText("");
    }

    private void showEmoticonPicker(Component anchor) {
        JPopupMenu popup = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(2, 4, 4, 4));
        grid.setBorder(Theme.padding(6, 6));
        grid.setBackground(Theme.SURFACE);
        for (String name : Emoticons.NAMES) {
            JButton b = new FlatButton("", FlatButton.Style.GHOST);
            ImageIcon icon = EmoticonIcons.get(name, 32);
            if (icon != null) b.setIcon(icon); else b.setText(name);
            b.setToolTipText(Emoticons.code(name));
            b.setBorder(Theme.padding(4, 4));
            b.addActionListener(e -> {
                input.insertEmoticon(name);
                popup.setVisible(false);
                input.requestFocus();
            });
            grid.add(b);
        }
        popup.add(grid);
        popup.show(anchor, 0, -grid.getPreferredSize().height - 16);
    }

    // ------------------------------------------------------------------ public API

    public void showRoom(String title, int max, String owner) {
        messages.removeAll();
        receiptRow = null;
        lastChatRow = null;
        lastSender = null;
        historyBanner = null;
        insertAt = -1;
        titleLabel.setText("# " + title);
        setOwner(owner);
        setMemberCount(1, max);
        cards.show(this, "room");
        messages.revalidate();
        messages.repaint();
        input.requestFocusInWindow();
    }

    /**
     * 방에 들어왔을 때 "이전 대화 N개가 있어요" 안내 줄을 맨 위에 보여준다.
     * 불러오기를 누르면 onLoad를 실행하고, 그 뒤 {@link #showHistory}가 이 자리에 이전 대화를 채운다.
     */
    public void showHistoryOffer(int count, Runnable onLoad) {
        JLabel label = new JLabel("이 방에 이전 대화 " + count + "개가 있어요.");
        label.setFont(Theme.font(12));
        label.setForeground(Theme.TEXT_MUTED);
        FlatButton load = new FlatButton("불러오기", FlatButton.Style.PRIMARY).radius(10);
        load.setBorder(Theme.padding(3, 10));
        FlatButton skip = new FlatButton("안 볼래요", FlatButton.Style.GHOST).radius(10);
        skip.setBorder(Theme.padding(3, 10));

        RoundedPanel pill = new RoundedPanel(new FlowLayout(FlowLayout.CENTER, 8, 0), Theme.SURFACE_LOW, 14);
        pill.setBorder(Theme.padding(6, 12));
        pill.add(label);
        pill.add(load);
        pill.add(skip);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
        row.add(pill);

        load.addActionListener(e -> {
            load.setEnabled(false);
            label.setText("이전 대화를 불러오는 중...");
            onLoad.run();
        });
        skip.addActionListener(e -> removeHistoryBanner());
        historyBanner = row;
        addRow(row);
    }

    /** CHAT_HISTORY 수신: 안내 줄이 있던 자리에 이전 대화를 채운다 */
    public void showHistory(java.util.List<String[]> rows) {
        if (historyBanner == null) return; // 이미 닫았거나 다른 방으로 옮겨 감
        int index = messages.getComponentZOrder(historyBanner);
        removeHistoryBanner();
        if (index < 0) index = 0;
        insertAt = index;
        lastSender = null;
        for (String[] r : rows) {
            if (r.length >= 3) appendChat(r[0], r[1], r[2]);
        }
        lastSender = null;
        appendNotice(rows.isEmpty() ? "불러올 이전 대화가 없어요." : "여기까지가 이전 대화예요.",
                Theme.TEXT_SUBTLE, Theme.SURFACE_LOW);
        insertAt = -1;
        lastSender = null;
        messages.revalidate();
        messages.repaint();
    }

    private void removeHistoryBanner() {
        if (historyBanner != null) {
            messages.remove(historyBanner);
            historyBanner = null;
            messages.revalidate();
            messages.repaint();
        }
    }

    public void showEmpty() {
        cards.show(this, "empty");
    }

    /** 방에 들어가기 전 화면(기본: 환영 문구)을 다른 화면(홈)으로 바꾼다 */
    public void setEmptyView(JComponent view) {
        remove(getComponent(0));
        add(view, "empty", 0);
        cards.show(this, "empty");
        revalidate();
    }

    public void setMemberCount(int current, int max) {
        countBadge.setText(current + "/" + max + "명");
        memberCount = current;
        updateDeleteButton();
    }

    public void setOwner(String owner) {
        ownerLabel.setText("방장 " + owner);
        this.owner = owner == null ? "" : owner;
        updateDeleteButton();
    }

    public void setOnDelete(Runnable onDelete) {
        this.onDelete = onDelete;
    }

    private void updateDeleteButton() {
        deleteButton.setVisible(owner.equals(myNickname) && memberCount <= 1);
    }

    public void appendChat(String from, String text, String time) {
        boolean mine = from.equals(myNickname);
        boolean continued = from.equals(lastSender);
        lastSender = from;

        // 내 메시지는 오른쪽(아바타·이름 없이), 다른 사람 메시지는 왼쪽
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(mine
                ? BorderFactory.createEmptyBorder(continued ? 2 : 10, 60, 0, 18)
                : BorderFactory.createEmptyBorder(continued ? 2 : 10, 18, 0, 60));

        if (!mine) {
            JLabel avatar = new JLabel(continued ? null : new Avatar(from, 36));
            avatar.setPreferredSize(new Dimension(36, continued ? 1 : 36));
            avatar.setVerticalAlignment(SwingConstants.TOP);
            JPanel avatarWrap = new JPanel(new BorderLayout());
            avatarWrap.setOpaque(false);
            avatarWrap.add(avatar, BorderLayout.NORTH);
            row.add(avatarWrap, BorderLayout.WEST);
        }

        JPanel body = new JPanel(new BorderLayout(0, 4));
        body.setOpaque(false);
        if (!continued) {
            JPanel head = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 0));
            head.setOpaque(false);
            JLabel t = new JLabel(time);
            t.setFont(Theme.font(11));
            t.setForeground(Theme.TEXT_SUBTLE);
            if (!mine) {
                JLabel name = new JLabel(from);
                name.setFont(Theme.bold(13));
                name.setForeground(Theme.avatarColor(from));
                head.add(name);
            }
            head.add(t);
            body.add(head, BorderLayout.NORTH);
        }

        // 이모티콘만 보낸 메시지는 말풍선 없이 이미지만 크게 보여준다
        JComponent bubble;
        if (isOnlyEmoticons(text)) {
            bubble = richText(text);
        } else {
            RoundedPanel box = new RoundedPanel(new BorderLayout(), mine ? Theme.PRIMARY_SOFT : Theme.SURFACE_LOW, 10);
            box.setBorder(Theme.padding(6, 10));
            box.add(richText(text), BorderLayout.CENTER);
            bubble = box;
        }
        JPanel bubbleWrap = new JPanel(new BorderLayout());
        bubbleWrap.setOpaque(false);
        bubbleWrap.add(bubble, mine ? BorderLayout.EAST : BorderLayout.WEST);
        body.add(bubbleWrap, BorderLayout.CENTER);
        row.add(body, BorderLayout.CENTER);

        addRow(row);
    }

    /** 서버에서 받은 실시간 채팅 (seq 포함). 읽음 표시를 갱신한다 */
    public void appendChat(String from, String text, String time, long seq) {
        appendChat(from, text, time);
        if (seq > lastSeq) {
            lastSeq = seq;
            lastChatMine = from.equals(myNickname);
            lastChatRow = lastAddedRow;
        }
        refreshReceipt();
    }

    /** 방에 들어올 때까지의 마지막 채팅 번호 (들어오면 거기까지 읽은 것으로 친다) */
    public void setLastSeq(long seq) {
        if (seq > lastSeq) lastSeq = seq;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    /** ROOM_MEMBERS 수신 시: 지금 방에 있는 사람들 */
    public void setRoomMembers(java.util.List<String> names) {
        roomMembers = java.util.List.copyOf(names);
        refreshReceipt();
    }

    /** CHAT_READ 수신 시: 각자 어디까지 읽었는지 */
    public void setReads(java.util.List<String[]> rows) {
        java.util.Map<String, Long> m = new java.util.HashMap<>();
        for (String[] r : rows) {
            try {
                m.put(r[0], Long.parseLong(r[1]));
            } catch (RuntimeException ignored) {
            }
        }
        reads = m;
        refreshReceipt();
    }

    /** 인스타 DM처럼, 내가 보낸 마지막 메시지 아래에 누가 읽었는지 보여준다 */
    private void refreshReceipt() {
        if (receiptRow != null) {
            messages.remove(receiptRow);
            receiptRow = null;
        }
        String text = receiptText();
        if (text != null) {
            JLabel label = new JLabel(text);
            label.setFont(Theme.font(11));
            label.setForeground(Theme.TEXT_SUBTLE);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 20));
            row.add(label);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            receiptRow = row;
            int index = lastChatRow == null ? -1 : messages.getComponentZOrder(lastChatRow);
            if (index >= 0) messages.add(row, index + 1); // 메시지 바로 밑
            else messages.add(row);
        }
        messages.revalidate();
        messages.repaint();
    }

    private String receiptText() {
        if (!lastChatMine || lastSeq <= 0) return null;
        java.util.List<String> others = new java.util.ArrayList<>();
        java.util.List<String> readers = new java.util.ArrayList<>();
        for (String n : roomMembers) {
            if (n.equals(myNickname)) continue;
            others.add(n);
            if (reads.getOrDefault(n, 0L) >= lastSeq) readers.add(n);
        }
        if (readers.isEmpty()) return null;
        if (others.size() >= 2 && readers.size() == others.size()) return "모두 읽음";
        if (readers.size() == 1) return readers.get(0) + "님이 읽음";
        if (readers.size() == 2) return readers.get(0) + "님과 " + readers.get(1) + "님이 읽음";
        return String.join("님, ", readers.subList(0, readers.size() - 1)) + "님과 "
                + readers.get(readers.size() - 1) + "님이 읽음";
    }

    public void appendSystem(String text) {
        appendNotice(text, Theme.TEXT_SUBTLE, Theme.SURFACE_LOW);
    }

    public void appendError(String text) {
        appendNotice(text, Theme.ERROR, Theme.ERROR_SOFT);
    }

    private void appendNotice(String text, Color fg, Color bg) {
        lastSender = null;
        JTextPane label = noticeText(text, fg);
        RoundedPanel pill = new RoundedPanel(new BorderLayout(), bg, 12);
        pill.setBorder(Theme.padding(4, 12));
        pill.add(label);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
        row.add(pill);
        addRow(row);
    }

    private void addRow(JComponent row) {
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        lastAddedRow = row;
        if (insertAt >= 0) { // 이전 대화를 위쪽에 채우는 중: 아래로 스크롤하지 않는다
            messages.add(row, insertAt++);
            messages.revalidate();
            return;
        }
        messages.add(row);
        messages.revalidate();
        SwingUtilities.invokeLater(() -> {
            messages.revalidate();
            Rectangle r = new Rectangle(0, messages.getHeight() + 1000, 1, 1);
            messages.scrollRectToVisible(r);
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
        });
    }

    /** 텍스트와 이모티콘 코드를 입력 순서대로 한 덩어리로 표시 */
    private static void invalidateTree(Container c) {
        c.invalidate();
        for (Component k : c.getComponents()) {
            if (k instanceof Container cc) invalidateTree(cc);
            else k.invalidate();
        }
    }

    /** 안내 문구: 창이 좁으면 여러 줄로 감싸서 가운데 정렬로 보여준다 */
    private JTextPane noticeText(String text, Color fg) {
        JTextPane pane = new JTextPane() {
            @Override
            public Dimension getPreferredSize() {
                int max = Math.max(120, scroll.getViewport().getWidth() - 100);
                setSize(max, Short.MAX_VALUE); // 이 폭으로 줄바꿈해 본 뒤
                Dimension d = super.getPreferredSize();
                int natural = getFontMetrics(Theme.font(12)).stringWidth(text) + 4;
                d.width = Math.min(max, natural); // 한 줄에 들어가면 글자 폭만큼만
                return d;
            }
        };
        pane.setEditable(false);
        pane.setFocusable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, Theme.font(12).getFamily());
        StyleConstants.setFontSize(a, 12);
        StyleConstants.setForeground(a, fg);
        StyleConstants.setAlignment(a, StyleConstants.ALIGN_CENTER);
        try {
            doc.insertString(0, text, a);
        } catch (BadLocationException ignored) {
        }
        doc.setParagraphAttributes(0, doc.getLength(), a, false);
        return pane;
    }

    /** 글자 없이 이모티콘만 있는 메시지인지 */
    private static boolean isOnlyEmoticons(String text) {
        java.util.List<Emoticons.Part> parts = Emoticons.split(text);
        return parts.stream().anyMatch(Emoticons.Part::isEmoticon)
                && parts.stream().allMatch(p -> p.isEmoticon() || p.text().isBlank());
    }

    private JTextPane richText(String text) {
        JTextPane pane = new JTextPane() {
            @Override
            public Dimension getPreferredSize() {
                // 긴 메시지는 줄바꿈되도록 최대 폭 제한
                Dimension d = super.getPreferredSize();
                int max = Math.max(200, scroll.getViewport().getWidth() - 160);
                if (d.width > max) {
                    setSize(max, Short.MAX_VALUE);
                    d = super.getPreferredSize();
                    d.width = max;
                }
                return d;
            }
        };
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setFont(Theme.font(14));
        pane.setForeground(Theme.TEXT);
        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet textStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(textStyle, Theme.font(14).getFamily());
        StyleConstants.setFontSize(textStyle, 14);
        StyleConstants.setForeground(textStyle, Theme.TEXT);
        boolean onlyEmoticons = isOnlyEmoticons(text);
        try {
            for (Emoticons.Part part : Emoticons.split(text)) {
                if (part.isEmoticon()) {
                    // 이모티콘만 있으면 크게, 글자와 섞이면 글자 크기에 맞게 작게
                    Icon icon = onlyEmoticons ? EmoticonIcons.get(part.emoticon(), 56)
                            : EmoticonIcons.inline(part.emoticon(), EmoticonInput.ICON_SIZE);
                    if (icon != null) {
                        SimpleAttributeSet iconStyle = new SimpleAttributeSet();
                        StyleConstants.setIcon(iconStyle, icon);
                        doc.insertString(doc.getLength(), " ", iconStyle);
                        continue;
                    }
                    doc.insertString(doc.getLength(), Emoticons.code(part.emoticon()), textStyle);
                } else {
                    doc.insertString(doc.getLength(), part.text(), textStyle);
                }
            }
        } catch (BadLocationException ignored) {
        }
        return pane;
    }

    /** 뷰포트 폭에 맞춰 늘어나는 세로 목록 (줄바꿈이 제대로 되도록 Scrollable 구현) */
    private static class MessageList extends JPanel implements Scrollable {
        MessageList() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Theme.SURFACE);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
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

    /** 코드로 그린 집 모양 아이콘 (이모지 글꼴에 의존하지 않는다) */
    private static final class HomeIcon implements javax.swing.Icon {
        private final int size;

        HomeIcon(int size) {
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
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.scale(size / 20.0, size / 20.0);
            g2.setColor(Theme.PRIMARY_DARK);
            java.awt.geom.Path2D roof = new java.awt.geom.Path2D.Double(); // 지붕
            roof.moveTo(10, 2);
            roof.lineTo(19, 10);
            roof.lineTo(16.5, 10);
            roof.lineTo(10, 4.6);
            roof.lineTo(3.5, 10);
            roof.lineTo(1, 10);
            roof.closePath();
            g2.fill(roof);
            java.awt.geom.Path2D body = new java.awt.geom.Path2D.Double(); // 몸체 (가운데 문이 비어 있다)
            body.moveTo(4, 10.5);
            body.lineTo(10, 5.6);
            body.lineTo(16, 10.5);
            body.lineTo(16, 18);
            body.lineTo(12, 18);
            body.lineTo(12, 13);
            body.lineTo(8, 13);
            body.lineTo(8, 18);
            body.lineTo(4, 18);
            body.closePath();
            g2.fill(body);
            g2.dispose();
        }
    }
}
