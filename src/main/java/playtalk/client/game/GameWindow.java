package playtalk.client.game;

import playtalk.client.ui.Theme;
import playtalk.client.ui.components.FlatButton;
import playtalk.client.ui.components.RoundedPanel;
import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 모든 게임 창의 공통 틀: 제목/참가자 헤더, 가운데 게임 화면, 아래 상태줄과 결과 배너.
 *
 * <p>새 게임 창은 이 클래스를 상속해 {@link #onStart}, {@link #onState}만 구현하면 된다.
 * 승패 판정은 서버가 하므로 창은 받은 상태를 그리기만 한다.
 */
public abstract class GameWindow extends JDialog {

    protected final GameSessionInfo session;
    /** 하위 클래스가 게임 화면을 넣는 곳 */
    protected final JPanel content = new JPanel(new BorderLayout());
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final RoundedPanel resultBanner = new RoundedPanel(new BorderLayout(), Theme.SURFACE_LOW, 10);
    private final JLabel resultLabel = new JLabel("", SwingConstants.CENTER);
    private final FlatButton closeButton = new FlatButton("기권하고 나가기", FlatButton.Style.GHOST);
    private boolean ended;

    protected GameWindow(JFrame owner, GameSessionInfo session, int width, int height) {
        super(owner, "PlayTalk - " + session.type().displayName(), false);
        this.session = session;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose();
            }
        });

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.SURFACE);
        root.setBorder(Theme.padding(18, 20));

        JLabel title = new JLabel(session.type().displayName());
        title.setFont(Theme.bold(20));
        title.setForeground(Theme.TEXT);
        JLabel players = new JLabel(String.join("  vs  ", session.players()));
        players.setFont(Theme.font(13));
        players.setForeground(Theme.TEXT_SUBTLE);
        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(players, BorderLayout.SOUTH);

        content.setOpaque(false);

        statusLabel.setFont(Theme.bold(14));
        statusLabel.setForeground(Theme.PRIMARY_DARK);
        resultLabel.setFont(Theme.bold(15));
        resultBanner.setBorder(Theme.padding(10, 12));
        resultBanner.add(resultLabel);
        resultBanner.setVisible(false);
        closeButton.addActionListener(e -> requestClose());

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        footer.add(statusLabel, BorderLayout.NORTH);
        footer.add(resultBanner, BorderLayout.CENTER);
        JPanel closeRow = new JPanel(new BorderLayout());
        closeRow.setOpaque(false);
        closeRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        closeRow.add(closeButton, BorderLayout.EAST);
        footer.add(closeRow, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(width, height);
        setLocationRelativeTo(owner);
    }

    /** GAME_START 수신 (창이 열린 직후) */
    public abstract void onStart(Message start);

    /** GAME_STATE 수신 */
    public abstract void onState(Message state);

    /** 게임이 끝났을 때 입력을 막는 등 추가 처리 */
    protected void onGameEnded() {
    }

    /** GAME_END 수신: 결과 배너 표시 */
    public final void onEnd(Message end) {
        ended = true;
        String myOutcome = Protocol.OUTCOME_DRAW;
        for (String[] r : end.getTable("results")) {
            if (r[0].equals(session.myNickname())) myOutcome = r[1];
        }
        String head;
        Color bg;
        Color fg;
        switch (myOutcome) {
            case Protocol.OUTCOME_WIN -> {
                head = "승리!";
                bg = Theme.PRIMARY_SOFT;
                fg = Theme.PRIMARY_DARK;
            }
            case Protocol.OUTCOME_LOSS -> {
                head = "패배";
                bg = Theme.ERROR_SOFT;
                fg = Theme.ERROR;
            }
            default -> {
                head = "무승부";
                bg = Theme.SURFACE_MID;
                fg = Theme.TEXT_MUTED;
            }
        }
        resultBanner.setBackground(bg);
        resultLabel.setForeground(fg);
        resultLabel.setText("<html><center>" + head + "<br><span style='font-weight:normal;font-size:11px'>"
                + escape(end.get("summary")) + "</span></center></html>");
        resultBanner.setVisible(true);
        setStatus(" ");
        closeButton.setText("닫기");
        closeButton.setStyle(FlatButton.Style.PRIMARY);
        onGameEnded();
        revalidate();
        repaint();
    }

    public boolean isEnded() {
        return ended;
    }

    protected void setStatus(String text) {
        statusLabel.setForeground(Theme.PRIMARY_DARK);
        statusLabel.setText(text);
    }

    public void showError(String text) {
        statusLabel.setForeground(Theme.ERROR);
        statusLabel.setText(text);
    }

    private void requestClose() {
        if (!ended) {
            int answer = JOptionPane.showConfirmDialog(this, "게임을 나가면 패배로 기록됩니다. 나갈까요?",
                    "기권", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            session.send(Message.of(MessageType.GAME_LEAVE).put("sessionId", session.sessionId()));
        }
        dispose();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
