package playtalk.client.ui;

import playtalk.client.ui.components.FlatButton;
import playtalk.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;

/** 새 채팅방 만들기: 제목과 최대 인원(2~5명)을 입력받는다. */
public class CreateRoomDialog extends JDialog {

    public record Result(String title, int max) {
    }

    private Result result;

    public CreateRoomDialog(JFrame owner) {
        super(owner, "새 채팅방 만들기", true);
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.SURFACE);
        root.setBorder(Theme.padding(20, 22));

        JLabel heading = new JLabel("새 채팅방 개설");
        heading.setFont(Theme.bold(18));
        heading.setForeground(Theme.TEXT);

        JTextField title = new JTextField();
        title.setFont(Theme.font(14));
        title.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), Theme.padding(8, 10)));

        JPanel maxRow = new JPanel(new GridLayout(1, Protocol.ROOM_MAX_USERS - Protocol.ROOM_MIN_USERS + 1, 6, 0));
        maxRow.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        JToggleButton[] options = new JToggleButton[Protocol.ROOM_MAX_USERS + 1];
        for (int n = Protocol.ROOM_MIN_USERS; n <= Protocol.ROOM_MAX_USERS; n++) {
            JToggleButton b = new JToggleButton(n + "명");
            b.setFont(Theme.bold(13));
            b.setFocusPainted(false);
            b.setBackground(Theme.SURFACE_LOW);
            b.addItemListener(e -> b.setForeground(b.isSelected() ? Theme.PRIMARY_DARK : Theme.TEXT_MUTED));
            group.add(b);
            maxRow.add(b);
            options[n] = b;
        }
        options[Protocol.ROOM_MAX_USERS].setSelected(true);

        JPanel form = new JPanel(new GridLayout(4, 1, 0, 6));
        form.setOpaque(false);
        form.add(label("방 제목"));
        form.add(title);
        form.add(label("최대 인원"));
        form.add(maxRow);

        JLabel error = new JLabel(" ");
        error.setForeground(Theme.ERROR);
        error.setFont(Theme.font(12));

        FlatButton cancel = new FlatButton("취소", FlatButton.Style.GHOST);
        FlatButton create = new FlatButton("만들기");
        cancel.addActionListener(e -> dispose());
        Runnable submit = () -> {
            String t = title.getText().trim();
            if (t.isEmpty() || t.length() > Protocol.ROOM_TITLE_MAX_LENGTH) {
                error.setText("방 제목은 1~" + Protocol.ROOM_TITLE_MAX_LENGTH + "자로 입력하세요.");
                return;
            }
            int max = Protocol.ROOM_MAX_USERS;
            for (int n = Protocol.ROOM_MIN_USERS; n <= Protocol.ROOM_MAX_USERS; n++) {
                if (options[n].isSelected()) max = n;
            }
            result = new Result(t, max);
            dispose();
        };
        create.addActionListener(e -> submit.run());
        title.addActionListener(e -> submit.run());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(create);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        root.add(heading, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(400, 330);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** 창을 띄우고 결과를 반환 (취소하면 null) */
    public Result showDialog() {
        setVisible(true);
        return result;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(12));
        l.setForeground(new Color(0x434655));
        return l;
    }
}
