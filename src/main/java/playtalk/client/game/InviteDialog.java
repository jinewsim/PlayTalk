package playtalk.client.game;

import playtalk.client.ui.Theme;
import playtalk.client.ui.components.Avatar;
import playtalk.client.ui.components.FlatButton;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/** 1:1 게임 초대를 받았을 때 뜨는 창 (수락/거절, 남은 시간 표시). */
public class InviteDialog extends JDialog {

    private final Timer timer;

    public InviteDialog(JFrame owner, String from, String gameName, int timeoutSeconds, Consumer<Boolean> onReply) {
        super(owner, "게임 초대", false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(14, 12));
        root.setBackground(Theme.SURFACE);
        root.setBorder(Theme.padding(18, 20));

        JLabel text = new JLabel("<html><b>" + from + "</b> 님이<br>[" + gameName + "] 대결을 신청했습니다!</html>");
        text.setFont(Theme.font(14));
        text.setForeground(Theme.TEXT);

        JProgressBar bar = new JProgressBar(0, timeoutSeconds * 10);
        bar.setValue(timeoutSeconds * 10);
        bar.setPreferredSize(new Dimension(0, 6));
        timer = new Timer(100, e -> {
            bar.setValue(bar.getValue() - 1);
            if (bar.getValue() <= 0) ((Timer) e.getSource()).stop();
        });
        timer.start();

        FlatButton decline = new FlatButton("거절", FlatButton.Style.GHOST);
        FlatButton accept = new FlatButton("수락");
        decline.addActionListener(e -> {
            onReply.accept(false);
            close();
        });
        accept.addActionListener(e -> {
            onReply.accept(true);
            close();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(decline);
        buttons.add(accept);

        JPanel south = new JPanel(new BorderLayout(0, 12));
        south.setOpaque(false);
        south.add(bar, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        root.add(new JLabel(new Avatar(from, 44)), BorderLayout.WEST);
        root.add(text, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(360, 190);
        setLocationRelativeTo(owner);
    }

    public void close() {
        timer.stop();
        dispose();
    }
}
