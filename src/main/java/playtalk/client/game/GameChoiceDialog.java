package playtalk.client.game;

import playtalk.client.ui.Theme;
import playtalk.client.ui.components.FlatButton;
import playtalk.client.ui.components.ScaledImageIcon;
import playtalk.common.GameType;

import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Color;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.QuadCurve2D;
import java.awt.RenderingHints;

/** 게임을 고르는 창: 게임마다 큰 카드 버튼을 보여주고, 누르면 바로 선택된다. */
public class GameChoiceDialog extends JDialog {

    /** 카드 위쪽 그림 칸의 높이 (게임마다 같아야 글자 위치가 맞는다) */
    private static final int ICON_HEIGHT = 56;

    private GameType selected;

    private GameChoiceDialog(JFrame owner, String title, String message, GameType[] options) {
        super(owner, title, true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.SURFACE);
        root.setBorder(Theme.padding(20, 22));

        JLabel msg = new JLabel(message);
        msg.setFont(Theme.bold(15));
        msg.setForeground(Theme.TEXT);

        JPanel row = new JPanel(new GridLayout(1, options.length, 12, 0));
        row.setOpaque(false);
        for (GameType type : options) {
            FlatButton card = new FlatButton(type.displayName(), FlatButton.Style.SECONDARY).radius(14);
            card.setIcon(iconFor(type));
            card.setVerticalTextPosition(SwingConstants.BOTTOM);
            card.setHorizontalTextPosition(SwingConstants.CENTER);
            card.setIconTextGap(14);
            card.setFont(Theme.bold(14));
            card.setPreferredSize(new Dimension(150, 135));
            card.addActionListener(e -> {
                selected = type;
                dispose();
            });
            row.add(card);
        }

        FlatButton cancel = new FlatButton("취소", FlatButton.Style.GHOST);
        cancel.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(cancel);

        root.add(msg, BorderLayout.NORTH);
        root.add(row, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** 창을 띄우고 고른 게임을 돌려준다. 취소하면 null. */
    public static GameType choose(JFrame owner, String title, String message, GameType[] options) {
        GameChoiceDialog dialog = new GameChoiceDialog(owner, title, message, options);
        dialog.setVisible(true); // 모달 창이라 닫힐 때까지 여기서 기다린다
        return dialog.selected;
    }

    /** 게임별 카드 그림. 새 게임을 추가하면 여기에 한 줄 추가 (그림 파일은 src/main/resources/games/) */
    private static Icon iconFor(GameType type) {
        return switch (type) {
            case TIC_TAC_TOE -> new GridIcon(ICON_HEIGHT);
            case ROCK_PAPER_SCISSORS -> new ScaledImageIcon("/games/rps.png", 120, ICON_HEIGHT);
            case TIMING -> new StopwatchIcon(ICON_HEIGHT);
            case BOMB -> new BombIcon(ICON_HEIGHT);
            default -> new TextIcon("🎮", 48, ICON_HEIGHT); // 🎮
        };
    }

    /** 틱택토: 3x3 표 */
    private static class GridIcon implements Icon {

        private final int box;

        GridIcon(int box) {
            this.box = box;
        }

        @Override
        public int getIconWidth() {
            return box;
        }

        @Override
        public int getIconHeight() {
            return box;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = (box - 8) / 3 * 3; // 3의 배수
            int ox = x + (box - size) / 2;
            int oy = y + (box - size) / 2;
            int cell = size / 3;
            g2.setColor(Theme.PRIMARY_DARK);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRoundRect(ox, oy, size, size, 10, 10);
            g2.drawLine(ox + cell, oy, ox + cell, oy + size);
            g2.drawLine(ox + 2 * cell, oy, ox + 2 * cell, oy + size);
            g2.drawLine(ox, oy + cell, ox + size, oy + cell);
            g2.drawLine(ox, oy + 2 * cell, ox + size, oy + 2 * cell);
            g2.dispose();
        }
    }

    /** 그림이 없는 게임을 위한 글자(이모지) 아이콘 */
    private static class TextIcon implements Icon {

        private final String text;
        private final int width;
        private final int height;

        TextIcon(String text, int width, int height) {
            this.text = text;
            this.width = width;
            this.height = height;
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font(Font.DIALOG, Font.PLAIN, 32));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, x + (width - fm.stringWidth(text)) / 2,
                    y + (height + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** 시간 맞추기: 스톱워치 */
    private static class StopwatchIcon implements Icon {

        private final int box;

        StopwatchIcon(int box) {
            this.box = box;
        }

        @Override
        public int getIconWidth() {
            return box;
        }

        @Override
        public int getIconHeight() {
            return box;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.scale(box / 56.0, box / 56.0);
            Color rim = new Color(0xF97316);
            // 윗 버튼과 옆 버튼
            g2.setColor(new Color(0x475569));
            g2.fill(new RoundRectangle2D.Double(22, 1, 12, 6, 3, 3));
            g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(28, 6, 28, 11);
            g2.setColor(rim);
            g2.drawLine(43, 13, 47, 9);
            // 몸통: 주황 테두리 + 흰 얼굴
            g2.setColor(rim);
            g2.fill(new Ellipse2D.Double(5, 9, 46, 46));
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(9, 13, 38, 38));
            // 지나간 시간 부채꼴
            g2.setColor(new Color(0xFED7AA));
            g2.fill(new Arc2D.Double(11, 15, 34, 34, 90, -100, Arc2D.PIE));
            // 눈금 4개
            g2.setColor(new Color(0x9A3412));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(28, 15, 28, 18);
            g2.drawLine(28, 47, 28, 44);
            g2.drawLine(11, 32, 14, 32);
            g2.drawLine(45, 32, 42, 32);
            // 바늘
            g2.setColor(new Color(0x1E293B));
            g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(28, 32, 28, 20);
            g2.drawLine(28, 32, 36, 37);
            g2.setColor(new Color(0xEF4444));
            g2.fill(new Ellipse2D.Double(25.5, 29.5, 5, 5));
            g2.dispose();
        }
    }

    /** 폭탄 돌리기: 폭탄 */
    private static class BombIcon implements Icon {

        private final int box;

        BombIcon(int box) {
            this.box = box;
        }

        @Override
        public int getIconWidth() {
            return box;
        }

        @Override
        public int getIconHeight() {
            return box;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.scale(box / 56.0, box / 56.0);
            g2.setColor(new Color(0x22262E));
            g2.fill(new Ellipse2D.Double(5, 15, 38, 38));
            g2.setColor(new Color(0x4A5160));
            g2.fill(new Ellipse2D.Double(12, 22, 9, 9));
            g2.setColor(new Color(0x8A6D3B));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new QuadCurve2D.Double(34, 18, 42, 8, 47, 6));
            g2.setColor(new Color(0xF59E0B));
            g2.fill(new Ellipse2D.Double(42, 0, 11, 11));
            g2.setColor(new Color(0xFDE68A));
            g2.fill(new Ellipse2D.Double(45, 3, 5, 5));
            g2.dispose();
        }
    }
}
