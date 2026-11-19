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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** 내가 보낸 1:1 게임 초대에 상대가 응답하기를 기다리는 동안 뜨는 창 (남은 시간, 신청 취소). */
public class WaitingDialog extends JDialog {

    private final Timer timer;

    public WaitingDialog(JFrame owner, String target, String gameName, int timeoutSeconds, Runnable onCancel) {
        super(owner, "대결 신청", false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(14, 12));
        root.setBackground(Theme.SURFACE);
        root.setBorder(Theme.padding(18, 20));

        JLabel text = new JLabel("<html><b>" + target + "</b> 님의 수락을<br>기다리는 중입니다...<br>"
                + "<span style='font-size:11px'>[" + gameName + "] 대결</span></html>");
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

        FlatButton cancelButton = new FlatButton("신청 취소", FlatButton.Style.GHOST);
        Runnable cancel = () -> {
            cancelButton.setEnabled(false);
            cancelButton.setText("취소 중...");
            onCancel.run();
        };
        cancelButton.addActionListener(e -> cancel.run());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel.run();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancelButton);

        JPanel south = new JPanel(new BorderLayout(0, 12));
        south.setOpaque(false);
        south.add(bar, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        root.add(new JLabel(new Avatar(target, 44)), BorderLayout.WEST);
        root.add(text, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(360, 190);
        setLocationRelativeTo(owner);
    }

    public void close() {
        dispose();
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }
}