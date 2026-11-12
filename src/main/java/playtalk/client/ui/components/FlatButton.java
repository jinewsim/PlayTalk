package playtalk.client.ui.components;

import playtalk.client.ui.Theme;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 둥근 모서리 평면 버튼. 스타일: PRIMARY(파란 배경), SECONDARY(연한 배경), GHOST(배경 없음), DANGER. */
public class FlatButton extends JButton {

    /** SOFT: 연한 배경, 마우스를 올려도 변하지 않음 (누를 수 없는 칸 표시용) */
    public enum Style { PRIMARY, SECONDARY, GHOST, DANGER, DARK, SOFT }

    private Style style;
    private boolean hover;
    private int radius = 8;

    public FlatButton(String text) {
        this(text, Style.PRIMARY);
    }

    public FlatButton(String text, Style style) {
        super(text);
        this.style = style;
        setFont(Theme.bold(13));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setBorder(Theme.padding(8, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
        applyForeground();
    }

    public FlatButton radius(int radius) {
        this.radius = radius;
        return this;
    }

    public void setStyle(Style style) {
        this.style = style;
        applyForeground();
        repaint();
    }

    private void applyForeground() {
        setForeground(switch (style) {
            case PRIMARY, DANGER -> Color.WHITE;
            case DARK -> Theme.TITLE_TEXT;
            case SECONDARY -> Theme.PRIMARY_DARK;
            case GHOST, SOFT -> Theme.TEXT_MUTED;
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color bg = switch (style) {
            case PRIMARY -> hover ? Theme.PRIMARY_DARK : Theme.PRIMARY;
            case DANGER -> hover ? Theme.ERROR.darker() : Theme.ERROR;
            case SECONDARY -> hover ? Theme.SURFACE_HIGH : Theme.SURFACE_MID;
            case DARK -> hover ? new Color(0x34465E) : new Color(0x2B3C53);
            case GHOST -> hover ? Theme.SURFACE_MID : null;
            case SOFT -> Theme.SURFACE_LOW;
        };
        if (!isEnabled()) {
            bg = Theme.BORDER_SOFT; // 비활성 글자색은 Look&Feel이 회색으로 그린다
        }
        if (bg != null) {
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
