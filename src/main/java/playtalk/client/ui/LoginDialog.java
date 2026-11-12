package playtalk.client.ui;

import playtalk.client.net.ServerConnection;
import playtalk.client.ui.components.FlatButton;
import playtalk.client.ui.components.RoundedPanel;
import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.util.function.BiConsumer;

/**
 * 서버 접속 화면: 서버 주소와 닉네임을 입력받아 접속한다.
 * 성공하면 onSuccess(연결, 닉네임)를 호출하고 창을 닫는다.
 */
public class LoginDialog extends JDialog {

    private final JTextField hostField = new JTextField("127.0.0.1:" + Protocol.DEFAULT_PORT);
    private final JTextField nicknameField = new JTextField();
    private final JLabel errorLabel = new JLabel(" ");
    private final FlatButton loginButton = new FlatButton("서버 접속 & 입장하기");
    private final BiConsumer<ServerConnection, String> onSuccess;

    public LoginDialog(BiConsumer<ServerConnection, String> onSuccess) {
        super((java.awt.Frame) null, "PlayTalk 접속", true);
        this.onSuccess = onSuccess;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(Theme.padding(28, 28));

        RoundedPanel card = new RoundedPanel(null, Theme.SURFACE, 16).border(Theme.BORDER_SOFT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(Theme.padding(28, 32));

        JLabel logo = new JLabel("PlayTalk", SwingConstants.CENTER);
        logo.setFont(Theme.bold(28));
        logo.setForeground(Theme.PRIMARY);
        JLabel subtitle = new JLabel("실시간 채팅 & 미니게임 메신저", SwingConstants.CENTER);
        subtitle.setFont(Theme.font(13));
        subtitle.setForeground(Theme.TEXT_SUBTLE);

        styleField(hostField);
        styleField(nicknameField);
        nicknameField.addActionListener(e -> attemptLogin());
        hostField.addActionListener(e -> nicknameField.requestFocus());

        errorLabel.setForeground(Theme.ERROR);
        errorLabel.setFont(Theme.font(12));

        loginButton.setPreferredSize(new Dimension(320, 42));
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginButton.addActionListener(e -> attemptLogin());

        card.add(center(logo));
        card.add(Box.createVerticalStrut(4));
        card.add(center(subtitle));
        card.add(Box.createVerticalStrut(24));
        card.add(labeled("서버 주소 (호스트:포트)", hostField));
        card.add(Box.createVerticalStrut(14));
        card.add(labeled("닉네임 (최대 " + Protocol.NICKNAME_MAX_LENGTH + "자)", nicknameField));
        card.add(Box.createVerticalStrut(8));
        card.add(left(errorLabel));
        card.add(Box.createVerticalStrut(8));
        card.add(loginButton);

        root.add(card);
        setContentPane(root);
        setSize(460, 470);
        setResizable(false);
        setLocationRelativeTo(null);
        nicknameField.requestFocusInWindow();
    }

    private void attemptLogin() {
        String nickname = nicknameField.getText().trim();
        String nickError = Protocol.validateNickname(nickname);
        if (nickError != null) {
            showError(nickError);
            return;
        }
        String host;
        int port;
        try {
            String[] hp = hostField.getText().trim().split(":");
            host = hp[0];
            port = hp.length > 1 ? Integer.parseInt(hp[1]) : Protocol.DEFAULT_PORT;
        } catch (NumberFormatException e) {
            showError("서버 주소 형식이 올바르지 않습니다. 예) 127.0.0.1:9090");
            return;
        }

        setBusy(true);
        ServerConnection conn = new ServerConnection();
        // 연결은 시간이 걸릴 수 있으므로 EDT 밖에서 수행
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                conn.connect(host, port);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    setBusy(false);
                    showError("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.");
                    return;
                }
                conn.on(MessageType.LOGIN_OK, msg -> {
                    dispose();
                    onSuccess.accept(conn, msg.get("nickname"));
                });
                conn.on(MessageType.LOGIN_FAIL, msg -> {
                    conn.close();
                    setBusy(false);
                    showError(msg.get("reason"));
                    nicknameField.selectAll();
                    nicknameField.requestFocus();
                });
                conn.onDisconnect(() -> {
                    setBusy(false);
                    showError("서버와의 연결이 끊어졌습니다.");
                });
                conn.send(Message.of(MessageType.LOGIN).put("nickname", nickname));
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        loginButton.setText(busy ? "접속 중..." : "서버 접속 & 입장하기");
        if (busy) errorLabel.setText(" ");
    }

    private void showError(String text) {
        errorLabel.setText(text);
    }

    private static void styleField(JTextField field) {
        field.setFont(Theme.font(14));
        field.setForeground(Theme.TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), Theme.padding(8, 10)));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static JComponent labeled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.bold(12));
        l.setForeground(Theme.TEXT_MUTED);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JComponent center(JComponent c) {
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return p;
    }

    private static JComponent left(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.WEST);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return p;
    }
}
