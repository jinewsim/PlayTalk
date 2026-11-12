package playtalk.client.game.games;

import playtalk.client.game.GameSessionInfo;
import playtalk.client.game.GameWindow;
import playtalk.client.ui.Theme;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * 시간 맞추기 (다인용). 목표 시간에 가장 가깝게 STOP을 누르면 승리.
 * 화면의 타이머는 처음 몇 초만 보이고 이후에는 숨겨진다. 실제 기록은 서버가 잰다.
 */
public class TimingWindow extends GameWindow {

    private final JLabel targetLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel clock = new JLabel("준비", SwingConstants.CENTER);
    private final FlatButton stop = new FlatButton("STOP  (Space)").radius(14);
    private final DefaultListModel<String> board = new DefaultListModel<>();
    private final Set<String> stopped = new HashSet<>();
    private Timer ticker;
    private long startNanos;
    private long targetMs = 10_000;
    private long visibleMs = 3_000;

    public TimingWindow(JFrame owner, GameSessionInfo session) {
        super(owner, session, 460, 560);
        targetLabel.setFont(Theme.font(14));
        targetLabel.setForeground(Theme.TEXT_MUTED);
        clock.setFont(Theme.bold(56));
        clock.setForeground(Theme.TEXT);
        stop.setFont(Theme.bold(20));
        stop.setPreferredSize(new Dimension(0, 64));
        stop.setEnabled(false);
        stop.addActionListener(e -> pressStop());

        // 스페이스바로도 STOP
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "stop");
        getRootPane().getActionMap().put("stop", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (stop.isEnabled()) pressStop();
            }
        });

        JList<String> list = new JList<>(board);
        list.setFont(Theme.font(13));
        list.setBackground(Theme.SURFACE_LOW);
        list.setFocusable(false);
        list.setFixedCellHeight(26);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(targetLabel, BorderLayout.NORTH);
        top.add(clock, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);
        center.add(stop, BorderLayout.CENTER);
        center.add(list, BorderLayout.SOUTH);
        content.add(center);
    }

    @Override
    public void onStart(Message start) {
        targetMs = start.getLong("target", 10_000);
        visibleMs = start.getLong("visibleMs", 3_000);
        targetLabel.setText(String.format("목표: 정확히 %.0f초에 STOP!  (타이머는 %.0f초 뒤 사라집니다)",
                targetMs / 1000.0, visibleMs / 1000.0));
        refreshBoard();
        setStatus("곧 시작합니다...");
    }

    @Override
    public void onState(Message state) {
        switch (state.get("event")) {
            case "COUNTDOWN" -> {
                clock.setText(state.get("count"));
                clock.setForeground(Theme.WARNING);
            }
            case "RUNNING" -> begin();
            case "STOPPED" -> {
                stopped.add(state.get("nickname"));
                refreshBoard();
            }
            case "LEFT" -> {
                stopped.add(state.get("nickname"));
                refreshBoard();
            }
            case "REVEAL" -> reveal(state);
            default -> {
            }
        }
    }

    private void begin() {
        startNanos = System.nanoTime();
        clock.setForeground(Theme.TEXT);
        stop.setEnabled(true);
        setStatus("지금이라고 생각될 때 STOP!");
        ticker = new Timer(30, e -> {
            long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
            clock.setText(elapsed < visibleMs ? String.format("%.2f", elapsed / 1000.0) : "?.??");
        });
        ticker.start();
    }

    private void pressStop() {
        stop.setEnabled(false);
        session.send(session.action().put("action", "STOP"));
        if (ticker != null) ticker.stop();
        clock.setText("STOP!");
        setStatus("다른 사람을 기다리는 중...");
    }

    private void reveal(Message state) {
        if (ticker != null) ticker.stop();
        stop.setEnabled(false);
        board.clear();
        int rank = 1;
        for (String[] r : state.getTable("records")) {
            long t = Long.parseLong(r[1]);
            long diff = Long.parseLong(r[2]);
            board.addElement(t < 0
                    ? "  -   " + r[0] + "  (기록 없음)"
                    : String.format("  %d위  %s   %.3f초  (오차 %.3f초)", rank++, r[0], t / 1000.0, diff / 1000.0));
        }
        clock.setText("결과");
    }

    private void refreshBoard() {
        board.clear();
        for (String p : session.players()) {
            board.addElement("  " + (stopped.contains(p) ? "[STOP] " : "[대기] ") + p + (p.equals(session.myNickname()) ? " (나)" : ""));
        }
    }

    @Override
    protected void onGameEnded() {
        if (ticker != null) ticker.stop();
        stop.setEnabled(false);
    }
}
