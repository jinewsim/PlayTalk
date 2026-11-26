package playtalk.client.ui;

import playtalk.client.ui.components.FlatButton;
import playtalk.client.ui.components.RoundedPanel;
import playtalk.common.Message;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 승률순 순위 창. 내 줄은 강조해서 보여준다.
 * 전체 랭킹({@link #global})과 채팅방 전적({@link #room}) 두 곳에서 같은 모양으로 쓴다.
 */
public class RankingDialog extends JDialog {

    /** 전체 랭킹에서 이 판 수 이상 한 사용자만 올린다 (1판 1승 = 100% 방지) */
    private static final int MIN_GAMES_FOR_RATE = 3;

    private record Entry(String name, int win, int loss, int draw) {
        int total() {
            return win + loss + draw;
        }

        double rate() {
            return total() == 0 ? 0 : (double) win / total();
        }
    }

    private final String me;
    private final int minGames;
    private final List<Entry> entries = new ArrayList<>();
    private final JPanel listPanel = new JPanel(new GridLayout(0, 1, 0, 6));

    /** 전체 사용자 랭킹 (RANKING 메시지) */
    public static RankingDialog global(JFrame owner, Message ranking, String me) {
        return new RankingDialog(owner, ranking, me, "랭킹", "PlayTalk 랭킹",
                MIN_GAMES_FOR_RATE + "판 이상 플레이한 사용자만 순위에 올라요. 승률이 높은 순서예요.", MIN_GAMES_FOR_RATE);
    }

    /** 이 채팅방에서 한 게임의 전적 (ROOM_STATS 메시지) */
    public static RankingDialog room(JFrame owner, Message stats, String me) {
        return new RankingDialog(owner, stats, me, "채팅방 전적", "이 채팅방의 전적",
                "이 채팅방에서 한 게임만 모아서 보여줘요. 승률이 높은 순서예요.", 1);
    }

    /** 나와 상대가 서로 한 1:1 게임의 전적 (VERSUS_STATS 메시지) */
    public static RankingDialog versus(JFrame owner, Message stats, String me) {
        return new RankingDialog(owner, stats, me, "상대 전적", me + " vs " + stats.get("opponent"),
                "두 사람이 서로 1:1로 한 게임만 모아서 보여줘요.", 0);
    }

    private RankingDialog(JFrame owner, Message ranking, String me, String windowTitle, String heading,
                          String noteText, int minGames) {
        super(owner, windowTitle, true);
        this.me = me;
        this.minGames = minGames;
        for (String[] r : ranking.getTable("users")) {
            entries.add(new Entry(r[0], Integer.parseInt(r[1]), Integer.parseInt(r[2]), Integer.parseInt(r[3])));
        }

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.SURFACE);
        root.setBorder(Theme.padding(20, 22));

        JLabel title = new JLabel(heading);
        title.setFont(Theme.bold(18));
        title.setForeground(Theme.TEXT);

        JLabel note = new JLabel(noteText);
        note.setFont(Theme.font(11));
        note.setForeground(Theme.TEXT_SUBTLE);

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(title, BorderLayout.NORTH);
        top.add(note, BorderLayout.CENTER);

        listPanel.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(listPanel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        FlatButton close = new FlatButton("닫기", FlatButton.Style.GHOST);
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(close);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(440, 560);
        setLocationRelativeTo(owner);
        showList();
    }

    private void showList() {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.removeIf(e -> e.total() < minGames);
        sorted.sort(Comparator.comparingDouble(Entry::rate).reversed()
                .thenComparing(Comparator.comparingInt(Entry::win).reversed())
                .thenComparing(Entry::name));

        listPanel.removeAll();
        if (sorted.isEmpty()) {
            JLabel empty = new JLabel("아직 전적이 없어요. 게임을 해보세요!", SwingConstants.CENTER);
            empty.setFont(Theme.font(13));
            empty.setForeground(Theme.TEXT_SUBTLE);
            empty.setBorder(Theme.padding(40, 0));
            listPanel.add(empty);
        }
        int rank = 1;
        for (Entry e : sorted) {
            listPanel.add(row(rank++, e));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel row(int rank, Entry e) {
        boolean mine = e.name().equals(me);
        RoundedPanel row = new RoundedPanel(new BorderLayout(12, 0),
                mine ? Theme.PRIMARY_SOFT : Theme.SURFACE_LOW, 10);
        row.setBorder(Theme.padding(8, 12));
        row.setPreferredSize(new Dimension(0, 48));

        JLabel rankLabel = new JLabel(String.valueOf(rank), SwingConstants.CENTER);
        rankLabel.setFont(Theme.bold(16));
        rankLabel.setForeground(rankColor(rank));
        rankLabel.setPreferredSize(new Dimension(30, 0));

        JLabel name = new JLabel(e.name() + (mine ? " (나)" : ""));
        name.setFont(Theme.bold(14));
        name.setForeground(Theme.TEXT);
        JLabel record = new JLabel(e.win() + "승 " + e.loss() + "패 " + e.draw() + "무");
        record.setFont(Theme.font(11));
        record.setForeground(Theme.TEXT_SUBTLE);
        JPanel mid = new JPanel(new GridLayout(2, 1));
        mid.setOpaque(false);
        mid.add(name);
        mid.add(record);

        String big = Math.round(e.rate() * 100) + "%";
        JLabel score = new JLabel(big, SwingConstants.RIGHT);
        score.setFont(Theme.bold(16));
        score.setForeground(Theme.PRIMARY_DARK);

        row.add(rankLabel, BorderLayout.WEST);
        row.add(mid, BorderLayout.CENTER);
        row.add(score, BorderLayout.EAST);
        return row;
    }

    private static Color rankColor(int rank) {
        return switch (rank) {
            case 1 -> new Color(0xD99A00); // 금
            case 2 -> new Color(0x8A94A6); // 은
            case 3 -> new Color(0xB4703A); // 동
            default -> Theme.TEXT_SUBTLE;
        };
    }
}
