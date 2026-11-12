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
import javax.swing.JTextArea;
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
    private final MessageList messages = new MessageList();
    private final JScrollPane scroll = new JScrollPane(messages);
    private final JTextArea input = new JTextArea(2, 20);
    private final String myNickname;
    private String lastSender;

    public ChatPanel(String myNickname, Consumer<String> onSend, Runnable onMultiGame, Runnable onLeave) {
        this.myNickname = myNickname;
        setLayout(cards);
        add(buildEmpty(), "empty");
        add(buildRoom(onSend, onMultiGame, onLeave), "room");
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

    private JComponent buildRoom(Consumer<String> onSend, Runnable onMultiGame, Runnable onLeave) {
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
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.add(titleLabel);
        titleRow.add(countBadge);
        titleRow.add(ownerLabel);

        FlatButton multi = new FlatButton("다인용 게임");
        multi.addActionListener(e -> onMultiGame.run());
        FlatButton leave = new FlatButton("나가기", FlatButton.Style.GHOST);
        leave.addActionListener(e -> onLeave.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(multi);
        actions.add(leave);
        header.add(titleRow, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        // ---- messages
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // ---- input
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
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
                    input.insert("\n", input.getCaretPosition());
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
        String text = input.getText().strip();
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
                input.insert(Emoticons.code(name), input.getCaretPosition());
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
        lastSender = null;
        titleLabel.setText("# " + title);
        setOwner(owner);
        setMemberCount(1, max);
        cards.show(this, "room");
        messages.revalidate();
        messages.repaint();
        input.requestFocusInWindow();
    }

    public void showEmpty() {
        cards.show(this, "empty");
    }

    public void setMemberCount(int current, int max) {
        countBadge.setText(current + "/" + max + "명");
    }

    public void setOwner(String owner) {
        ownerLabel.setText("방장 " + owner);
    }

    public void appendChat(String from, String text, String time) {
        boolean mine = from.equals(myNickname);
        boolean continued = from.equals(lastSender);
        lastSender = from;

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(continued ? 2 : 10, 18, 0, 60));

        JLabel avatar = new JLabel(continued ? null : new Avatar(from, 36));
        avatar.setPreferredSize(new Dimension(36, continued ? 1 : 36));
        avatar.setVerticalAlignment(SwingConstants.TOP);
        JPanel avatarWrap = new JPanel(new BorderLayout());
        avatarWrap.setOpaque(false);
        avatarWrap.add(avatar, BorderLayout.NORTH);
        row.add(avatarWrap, BorderLayout.WEST);

        JPanel body = new JPanel(new BorderLayout(0, 4));
        body.setOpaque(false);
        if (!continued) {
            JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            head.setOpaque(false);
            JLabel name = new JLabel(from + (mine ? " (나)" : ""));
            name.setFont(Theme.bold(13));
            name.setForeground(Theme.avatarColor(from));
            JLabel t = new JLabel(time);
            t.setFont(Theme.font(11));
            t.setForeground(Theme.TEXT_SUBTLE);
            head.add(name);
            head.add(t);
            body.add(head, BorderLayout.NORTH);
        }

        RoundedPanel bubble = new RoundedPanel(new BorderLayout(), mine ? Theme.PRIMARY_SOFT : Theme.SURFACE_LOW, 10);
        bubble.setBorder(Theme.padding(6, 10));
        bubble.add(richText(text), BorderLayout.CENTER);
        JPanel bubbleWrap = new JPanel(new BorderLayout());
        bubbleWrap.setOpaque(false);
        bubbleWrap.add(bubble, BorderLayout.WEST);
        body.add(bubbleWrap, BorderLayout.CENTER);
        row.add(body, BorderLayout.CENTER);

        addRow(row);
    }

    public void appendSystem(String text) {
        appendNotice(text, Theme.TEXT_SUBTLE, Theme.SURFACE_LOW);
    }

    public void appendError(String text) {
        appendNotice(text, Theme.ERROR, Theme.ERROR_SOFT);
    }

    private void appendNotice(String text, Color fg, Color bg) {
        lastSender = null;
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(12));
        label.setForeground(fg);
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
        boolean onlyEmoticons = Emoticons.split(text).stream().allMatch(p -> p.isEmoticon() || p.text().isBlank());
        try {
            for (Emoticons.Part part : Emoticons.split(text)) {
                if (part.isEmoticon()) {
                    Icon icon = EmoticonIcons.get(part.emoticon(), onlyEmoticons ? 56 : 24);
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
}
