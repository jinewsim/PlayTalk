package playtalk.client.game.games;

import playtalk.client.game.GameSessionInfo;
import playtalk.client.game.GameWindow;
import playtalk.client.ui.Theme;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;

import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 폭탄 돌리기 (다인용). 폭탄을 가진 사람이 다른 사람의 이름 버튼을 눌러 넘긴다.
 * 언제 터질지는 서버만 알고 있다.
 */
public class BombWindow extends GameWindow {

    /** DANGER 신호를 받고 나서 위험도가 최대가 되기까지 걸리는 시간(ms) */
    private static final long DANGER_RAMP_MS = 3_000;

    private final BombIcon bombIcon = new BombIcon(96);
    private final JLabel bombLabel = new JLabel(bombIcon, SwingConstants.CENTER);
    private Timer animation;
    private long dangerSince;
    private final JLabel holderLabel = new JLabel(" ", SwingConstants.CENTER);
    private final Map<String, FlatButton> buttons = new LinkedHashMap<>();
    private String holder = "";
    private boolean over;

    public BombWindow(JFrame owner, GameSessionInfo session) {
        super(owner, session, 440, 560);
        holderLabel.setFont(Theme.bold(18));
        holderLabel.setForeground(Theme.TEXT);

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(bombLabel, BorderLayout.CENTER);
        top.add(holderLabel, BorderLayout.SOUTH);

        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10));
        grid.setOpaque(false);
        for (String p : session.players()) {
            FlatButton b = new FlatButton(p.equals(session.myNickname()) ? p + " (나)" : p).radius(12);
            b.setFont(Theme.bold(15));
            b.setPreferredSize(new Dimension(0, 52));
            b.setEnabled(false);
            b.addActionListener(e -> pass(p));
            buttons.put(p, b);
            grid.add(b);
        }

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);
        center.add(grid, BorderLayout.CENTER);
        content.add(center);
    }

    @Override
    public void onStart(Message start) {
        setStatus("폭탄이 곧 배정됩니다...");
    }

    @Override
    public void onState(Message state) {
        switch (state.get("event")) {
            case "HOLDER" -> {
                holder = state.get("nickname");
                String from = state.get("from", "");
                boolean mine = holder.equals(session.myNickname());
                holderLabel.setText(mine ? "폭탄이 내게 왔어요!" : holder + " 님이 폭탄을 들고 있어요");
                holderLabel.setForeground(mine ? Theme.ERROR : Theme.TEXT);
                setStatus(mine ? "빨리 다른 사람에게 넘기세요!"
                        : (from.isEmpty() ? "" : from + " → " + holder + " 로 폭탄이 넘어갔습니다"));
                refreshButtons();
            }
            case "LEFT" -> {
                FlatButton b = buttons.get(state.get("nickname"));
                if (b != null) {
                    b.setText(state.get("nickname") + " (나감)");
                    b.setEnabled(false);
                }
            }
            case "DANGER" -> startDanger();
            case "EXPLODE" -> {
                over = true;
                stopAnimation();
                bombIcon.set(0, true, 0);
                bombLabel.repaint();
                holderLabel.setText("펑! " + state.get("nickname") + " 님이 터졌어요");
                holderLabel.setForeground(Theme.ERROR);
                refreshButtons();
            }
            default -> {
            }
        }
    }

    /** 폭발 직전: 폭탄이 점점 더 심하게 떨리고 붉어진다 */
    private void startDanger() {
        if (over) return;
        dangerSince = System.nanoTime();
        setStatus("곧 터질 것 같아요...!");
        stopAnimation();
        animation = new Timer(30, e -> {
            long ms = (System.nanoTime() - dangerSince) / 1_000_000;
            bombIcon.set(Math.min(1.0, 0.35 + 0.65 * ms / DANGER_RAMP_MS), false, ms);
            bombLabel.repaint();
        });
        animation.start();
    }

    private void stopAnimation() {
        if (animation != null) animation.stop();
    }

    private void pass(String target) {
        if (over || !holder.equals(session.myNickname())) return;
        session.send(session.action().put("action", "PASS").put("target", target));
    }

    /** 내가 폭탄을 들고 있을 때만, 나를 뺀 남은 사람 버튼을 켠다 */
    private void refreshButtons() {
        boolean mine = !over && holder.equals(session.myNickname());
        for (Map.Entry<String, FlatButton> e : buttons.entrySet()) {
            String name = e.getKey();
            boolean left = e.getValue().getText().endsWith("(나감)");
            e.getValue().setEnabled(mine && !name.equals(session.myNickname()) && !left);
            e.getValue().setStyle(name.equals(holder) ? FlatButton.Style.DANGER : FlatButton.Style.SECONDARY);
        }
    }

    @Override
    protected void onGameEnded() {
        over = true;
        stopAnimation();
        refreshButtons();
    }

    /**
     * 코드로 그린 폭탄 (이모지 글꼴에 의존하지 않는다).
     * danger(0~1)가 커질수록 빨개지고 떨리며 불꽃이 커진다. exploded면 폭발 그림.
     */
    private static final class BombIcon implements Icon {
        private final int size;
        private double danger;
        private boolean exploded;
        private long phase;

        BombIcon(int size) {
            this.size = size;
        }

        void set(double danger, boolean exploded, long phase) {
            this.danger = danger;
            this.exploded = exploded;
            this.phase = phase;
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
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.scale(size / 96.0, size / 96.0);
            if (exploded) {
                paintBlast(g2);
            } else {
                paintBomb(g2);
            }
            g2.dispose();
        }

        private void paintBomb(Graphics2D g2) {
            double t = phase / 1000.0;
            // 위험할수록 빠르고 크게 떨린다
            double amp = 4.0 * danger;
            g2.translate(Math.sin(t * 60) * amp, Math.cos(t * 47) * amp * 0.6);
            double pulse = (Math.sin(t * (8 + 14 * danger)) + 1) / 2; // 0~1 깜빡임

            if (danger > 0) { // 몸통 뒤의 붉은 빛
                int alpha = (int) (60 + 120 * danger * pulse);
                g2.setColor(new Color(0xEF, 0x44, 0x44, Math.min(alpha, 200)));
                double r = 34 + 6 * danger * pulse;
                g2.fill(new Ellipse2D.Double(45 - r, 56 - r, r * 2, r * 2));
            }
            Color body = blend(new Color(0x22262E), new Color(0x7F1D1D), danger * pulse);
            g2.setColor(body);
            g2.fill(new Ellipse2D.Double(14, 28, 62, 62));
            g2.setColor(new Color(0x4A5160));
            g2.fill(new Ellipse2D.Double(26, 40, 14, 14)); // 반짝임
            g2.setColor(new Color(0x8A6D3B));
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new QuadCurve2D.Double(60, 34, 72, 18, 80, 14)); // 심지
            double spark = 16 + 10 * danger + 4 * danger * pulse;    // 불꽃 크기
            g2.setColor(new Color(0xF59E0B));
            g2.fill(new Ellipse2D.Double(82 - spark / 2, 12 - spark / 2, spark, spark));
            g2.setColor(new Color(0xFDE68A));
            g2.fill(new Ellipse2D.Double(82 - spark / 4, 12 - spark / 4, spark / 2, spark / 2));
        }

        /** 터진 모습: 뾰족한 별 모양 */
        private void paintBlast(Graphics2D g2) {
            Path2D star = new Path2D.Double();
            int points = 12;
            for (int i = 0; i < points * 2; i++) {
                double r = i % 2 == 0 ? 46 : 24;
                double a = Math.PI * i / points;
                double px = 48 + r * Math.cos(a), py = 48 + r * Math.sin(a);
                if (i == 0) star.moveTo(px, py);
                else star.lineTo(px, py);
            }
            star.closePath();
            g2.setColor(new Color(0xEF4444));
            g2.fill(star);
            g2.setColor(new Color(0xF59E0B));
            g2.scale(0.62, 0.62);
            g2.translate(29.5, 29.5);
            g2.fill(star);
            g2.setColor(new Color(0xFDE68A));
            g2.scale(0.55, 0.55);
            g2.translate(38, 38);
            g2.fill(star);
        }

        private static Color blend(Color a, Color b, double t) {
            return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                    (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }
    }
}
