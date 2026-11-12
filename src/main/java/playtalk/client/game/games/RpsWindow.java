package playtalk.client.game.games;

import playtalk.client.game.GameSessionInfo;
import playtalk.client.game.GameWindow;
import playtalk.client.ui.Theme;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Map;

/** 가위바위보: 제한 시간 안에 하나를 고른다. 결과는 서버가 판정해 알려준다. */
public class RpsWindow extends GameWindow {

    private static final String[][] CHOICES = {{"SCISSORS", "가위"}, {"ROCK", "바위"}, {"PAPER", "보"}};
    private static final Map<String, String> LABELS = Map.of(
            "SCISSORS", "가위", "ROCK", "바위", "PAPER", "보", "NONE", "선택 안 함");

    private final FlatButton[] buttons = new FlatButton[3];
    private final JProgressBar timeBar = new JProgressBar();
    private final JLabel info = new JLabel("하나를 고르세요!", SwingConstants.CENTER);
    private Timer countdown;
    private boolean chosen;

    public RpsWindow(JFrame owner, GameSessionInfo session) {
        super(owner, session, 440, 420);
        JPanel row = new JPanel(new GridLayout(1, 3, 10, 0));
        row.setOpaque(false);
        for (int i = 0; i < 3; i++) {
            String code = CHOICES[i][0];
            FlatButton b = new FlatButton(CHOICES[i][1], FlatButton.Style.SECONDARY).radius(14);
            b.setFont(Theme.bold(24));
            b.setPreferredSize(new Dimension(110, 110));
            b.addActionListener(e -> choose(code, b));
            buttons[i] = b;
            row.add(b);
        }
        info.setFont(Theme.font(14));
        info.setForeground(Theme.TEXT_MUTED);
        timeBar.setPreferredSize(new Dimension(0, 8));

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.add(timeBar, BorderLayout.NORTH);
        center.add(row, BorderLayout.CENTER);
        center.add(info, BorderLayout.SOUTH);
        content.add(center);
    }

    @Override
    public void onStart(Message start) {
        int limit = start.getInt("limit", 10);
        timeBar.setMaximum(limit * 10);
        timeBar.setValue(limit * 10);
        countdown = new Timer(100, e -> {
            timeBar.setValue(timeBar.getValue() - 1);
            if (timeBar.getValue() <= 0) ((Timer) e.getSource()).stop();
        });
        countdown.start();
        setStatus("제한 시간 " + limit + "초");
    }

    private void choose(String code, FlatButton button) {
        if (chosen || isEnded()) return;
        chosen = true;
        session.send(session.action().put("choice", code));
        // 비활성화(회색) 대신 고른 카드만 강조하고 나머지는 흐리게
        for (FlatButton b : buttons) b.setStyle(b == button ? FlatButton.Style.PRIMARY : FlatButton.Style.SOFT);
        info.setText("선택 완료! 상대를 기다리는 중...");
    }

    @Override
    public void onState(Message state) {
        switch (state.get("event")) {
            case "CHOSEN" -> {
                if (!state.get("nickname").equals(session.myNickname())) {
                    setStatus(state.get("nickname") + " 님이 선택했습니다!");
                }
            }
            case "REVEAL" -> {
                StringBuilder sb = new StringBuilder();
                for (String[] r : state.getTable("choices")) {
                    if (sb.length() > 0) sb.append("   vs   ");
                    sb.append(r[0]).append(" : ").append(LABELS.getOrDefault(r[1], r[1]));
                }
                info.setText(sb.toString());
                info.setFont(Theme.bold(14));
            }
            default -> {
            }
        }
    }

    @Override
    protected void onGameEnded() {
        if (countdown != null) countdown.stop();
        if (!chosen) {
            for (FlatButton b : buttons) b.setStyle(FlatButton.Style.SOFT);
        }
    }
}
