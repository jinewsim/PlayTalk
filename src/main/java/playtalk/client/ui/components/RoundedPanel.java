package playtalk.client.ui.components;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** 둥근 모서리 배경(+선택적 테두리)을 그리는 패널. 카드, 말풍선 등에 사용. */
public class RoundedPanel extends JPanel {

    private final int radius;
    private Color borderColor;

    public RoundedPanel(LayoutManager layout, Color background, int radius) {
        super(layout);
        this.radius = radius;
        setBackground(background);
        setOpaque(false);
    }

    public RoundedPanel border(Color color) {
        this.borderColor = color;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius * 2, radius * 2);
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius * 2, radius * 2);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
