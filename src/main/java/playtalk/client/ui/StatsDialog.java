package playtalk.client.ui;

import playtalk.client.ui.components.RoundedPanel;
import playtalk.common.GameType;
import playtalk.common.Message;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;

/** 내 전적 창: 총 승/패/무, 승률, 게임별 기록. */
public class StatsDialog extends JDialog {

    public StatsDialog(JFrame owner, Message stats) {
        super(owner, "전적 - " + stats.get("nickname"), true);
        int win = stats.getInt("win", 0), loss = stats.getInt("loss", 0), draw = stats.getInt("draw", 0);
        int total = win + loss + draw;
        int rate = total == 0 ? 0 : Math.round(win * 100f / total);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.SURFACE);
        root.setBorder(Theme.padding(20, 22));

        JLabel heading = new JLabel(stats.get("nickname") + " 님의 미니게임 전적");
        heading.setFont(Theme.bold(18));
        heading.setForeground(Theme.TEXT);

        JPanel tiles = new JPanel(new GridLayout(1, 4, 8, 0));
        tiles.setOpaque(false);
        tiles.add(tile("총 대전", String.valueOf(total), Theme.TEXT));
        tiles.add(tile("승리", String.valueOf(win), Theme.PRIMARY_DARK));
        tiles.add(tile("패배", String.valueOf(loss), Theme.ERROR));
        tiles.add(tile("무승부", String.valueOf(draw), Theme.TEXT_MUTED));

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(rate);
        bar.setStringPainted(true);
        bar.setString("승률 " + rate + "%");
        bar.setFont(Theme.bold(12));
        bar.setPreferredSize(new java.awt.Dimension(0, 22));

        JPanel games = new JPanel(new GridLayout(0, 1, 0, 6));
        games.setOpaque(false);
        for (String[] r : stats.getTable("games")) {
            GameType type = GameType.parse(r[0]);
            if (type == null) continue;
            JPanel row = new RoundedPanel(new BorderLayout(), Theme.SURFACE_LOW, 8);
            row.setBorder(Theme.padding(8, 12));
            JLabel name = new JLabel(type.displayName());
            name.setFont(Theme.bold(13));
            JLabel rec = new JLabel(r[1] + "승 " + r[2] + "패 " + r[3] + "무");
            rec.setFont(Theme.font(13));
            rec.setForeground(Theme.TEXT_MUTED);
            row.add(name, BorderLayout.WEST);
            row.add(rec, BorderLayout.EAST);
            games.add(row);
        }

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(tiles, BorderLayout.NORTH);
        center.add(bar, BorderLayout.CENTER);
        center.add(games, BorderLayout.SOUTH);

        root.add(heading, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        setContentPane(root);
        pack();
        setSize(Math.max(getWidth(), 440), getHeight());
        setLocationRelativeTo(owner);
    }

    private static JPanel tile(String label, String value, Color color) {
        RoundedPanel p = new RoundedPanel(new BorderLayout(), Theme.SURFACE_LOW, 10);
        p.setBorder(BorderFactory.createEmptyBorder(10, 6, 10, 6));
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setFont(Theme.bold(22));
        v.setForeground(color);
        JLabel l = new JLabel(label, SwingConstants.CENTER);
        l.setFont(Theme.font(11));
        l.setForeground(Theme.TEXT_SUBTLE);
        p.add(v, BorderLayout.CENTER);
        p.add(l, BorderLayout.SOUTH);
        return p;
    }
}
