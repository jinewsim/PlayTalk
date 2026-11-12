package playtalk.client.ui.components;

import playtalk.client.ui.Theme;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** 닉네임 첫 글자를 넣은 동그란 아바타 아이콘 (+상태 점). */
public class Avatar implements Icon {

    private final String nickname;
    private final int size;
    private final Color statusColor;

    public Avatar(String nickname, int size) {
        this(nickname, size, null);
    }

    public Avatar(String nickname, int size, Color statusColor) {
        this.nickname = nickname == null || nickname.isEmpty() ? "?" : nickname;
        this.size = size;
        this.statusColor = statusColor;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Theme.avatarColor(nickname));
        g2.fillOval(x, y, size, size);

        String initial = nickname.substring(0, Character.charCount(nickname.codePointAt(0))).toUpperCase();
        Font font = Theme.bold(Math.max(10, size * 2 / 5));
        g2.setFont(font);
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(initial, x + (size - fm.stringWidth(initial)) / 2,
                y + (size - fm.getHeight()) / 2 + fm.getAscent());

        if (statusColor != null) {
            int d = Math.max(8, size / 3);
            g2.setColor(Color.WHITE);
            g2.fillOval(x + size - d, y + size - d, d, d);
            g2.setColor(statusColor);
            g2.fillOval(x + size - d + 2, y + size - d + 2, d - 4, d - 4);
        }
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
