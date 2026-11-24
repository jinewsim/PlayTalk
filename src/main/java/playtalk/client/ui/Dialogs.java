package playtalk.client.ui;

import playtalk.client.ui.components.FlatButton;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * 앱 테마에 맞춘 알림 / 확인 / 비밀번호 확인 창 (JOptionPane 대신 사용).
 * <pre>
 *  ( ! )  친구를 삭제할까요?
 *         yujin 님이 친구 목록에서 사라져요.
 *                                [취소] [삭제]
 * </pre>
 * Enter = 확인, Esc = 취소.
 */
public final class Dialogs {

    private Dialogs() {
    }

    /** 창 종류: 아이콘 모양과 확인 버튼 색이 달라진다 */
    public enum Kind {
        INFO('i', Theme.PRIMARY, Theme.PRIMARY_SOFT),
        QUESTION('?', Theme.PRIMARY, Theme.PRIMARY_SOFT),
        WARNING('!', Theme.WARNING, new Color(0xFEF3C7)),
        /** 되돌릴 수 없는 동작 (삭제, 탈퇴 등): 빨간 확인 버튼 */
        DANGER('!', Theme.ERROR, Theme.ERROR_SOFT);

        final char symbol;
        final Color color;
        final Color soft;

        Kind(char symbol, Color color, Color soft) {
            this.symbol = symbol;
            this.color = color;
            this.soft = soft;
        }
    }

    /** 확인 버튼 하나짜리 알림 */
    public static void alert(Component parent, Kind kind, String heading, String message) {
        new ThemedDialog(parent, kind, heading, message, null, "확인", null).open();
    }

    /** @return 확인을 눌렀으면 true */
    public static boolean confirm(Component parent, Kind kind, String heading, String message, String okText) {
        return new ThemedDialog(parent, kind, heading, message, null, okText, "취소").open();
    }

    /**
     * 비밀번호를 한 번 더 입력받는 확인 창.
     *
     * @return 입력한 비밀번호 (취소하면 null)
     */
    public static String password(Component parent, Kind kind, String heading, String message, String okText) {
        JPasswordField field = new JPasswordField();
        field.setFont(Theme.font(14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), Theme.padding(8, 10)));
        ThemedDialog d = new ThemedDialog(parent, kind, heading, message, field, okText, "취소");
        d.validator = () -> field.getPassword().length == 0 ? "비밀번호를 입력하세요." : null;
        return d.open() ? new String(field.getPassword()) : null;
    }

    // ------------------------------------------------------------------ 구현

    private static final class ThemedDialog extends JDialog {
        private final JLabel error = new JLabel(" ");
        private java.util.function.Supplier<String> validator = () -> null;
        private boolean confirmed;

        ThemedDialog(Component parent, Kind kind, String heading, String message,
                     JComponent extra, String okText, String cancelText) {
            super(windowOf(parent), headingTitle(kind), ModalityType.APPLICATION_MODAL);

            JPanel root = new JPanel(new BorderLayout(16, 0));
            root.setBackground(Theme.SURFACE);
            root.setBorder(Theme.padding(22, 24));

            JLabel icon = new JLabel(new SymbolIcon(kind, 44));
            icon.setVerticalAlignment(JLabel.TOP);
            root.add(icon, BorderLayout.WEST);

            JPanel text = new JPanel(new BorderLayout(0, 8));
            text.setOpaque(false);
            JLabel h = new JLabel(heading);
            h.setFont(Theme.bold(16));
            h.setForeground(Theme.TEXT);
            text.add(h, BorderLayout.NORTH);
            if (message != null && !message.isEmpty()) {
                JLabel m = new JLabel("<html><div style='width:260px'>" + escape(message).replace("\n", "<br>") + "</div></html>");
                m.setFont(Theme.font(13));
                m.setForeground(Theme.TEXT_MUTED);
                text.add(m, BorderLayout.CENTER);
            }
            JPanel extraBox = new JPanel(new BorderLayout(0, 4));
            extraBox.setOpaque(false);
            if (extra != null) {
                extraBox.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
                extraBox.add(extra, BorderLayout.CENTER);
                error.setFont(Theme.font(12));
                error.setForeground(Theme.ERROR);
                extraBox.add(error, BorderLayout.SOUTH);
            }
            text.add(extraBox, BorderLayout.SOUTH);
            root.add(text, BorderLayout.CENTER);

            FlatButton ok = new FlatButton(okText, kind == Kind.DANGER ? FlatButton.Style.DANGER : FlatButton.Style.PRIMARY);
            ok.setBorder(Theme.padding(8, 18));
            ok.addActionListener(e -> {
                String problem = validator.get();
                if (problem != null) {
                    error.setText(problem);
                    return;
                }
                confirmed = true;
                dispose();
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            buttons.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
            if (cancelText != null) {
                FlatButton cancel = new FlatButton(cancelText, FlatButton.Style.GHOST);
                cancel.setBorder(Theme.padding(8, 16));
                cancel.addActionListener(e -> dispose());
                buttons.add(cancel);
            }
            buttons.add(ok);
            root.add(buttons, BorderLayout.SOUTH);

            setContentPane(root);
            getRootPane().setDefaultButton(ok);
            getRootPane().registerKeyboardAction(e -> dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            setResizable(false);
            pack();
            setLocationRelativeTo(windowOf(parent));
            if (extra != null) {
                addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowOpened(java.awt.event.WindowEvent e) {
                        extra.requestFocusInWindow();
                    }
                });
            }
        }

        boolean open() {
            setVisible(true); // 모달: 닫힐 때까지 대기
            return confirmed;
        }

        private static String headingTitle(Kind kind) {
            return switch (kind) {
                case INFO -> "알림";
                case QUESTION -> "확인";
                case WARNING -> "주의";
                case DANGER -> "확인";
            };
        }

        private static Window windowOf(Component c) {
            if (c == null) return null;
            return c instanceof Window w ? w : SwingUtilities.getWindowAncestor(c);
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /** 연한 원 안에 기호(i ? !)를 그린 아이콘. 글꼴에 따라 깨지지 않도록 직접 그린다. */
    private record SymbolIcon(Kind kind, int size) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(kind.soft);
            g2.fillOval(x, y, size, size);
            g2.setColor(kind.color);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(x + 1, y + 1, size - 2, size - 2);
            g2.setFont(Theme.bold(size / 2));
            FontMetrics fm = g2.getFontMetrics();
            String s = String.valueOf(kind.symbol);
            g2.drawString(s, x + (size - fm.stringWidth(s)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
