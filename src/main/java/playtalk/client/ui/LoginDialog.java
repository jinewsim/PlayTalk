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
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;

/**
 * 로그인 / 회원가입 화면.
 *
 * <p>로그인: 서버 주소, 닉네임, 비밀번호를 입력해 접속한다.
 * 회원가입: 비밀번호 확인까지 입력하면 계정을 만든 뒤(REGISTER) 바로 로그인(LOGIN)한다.
 * 성공하면 onSuccess(연결, 닉네임)를 호출하고 창을 닫는다.
 */
public class LoginDialog extends JDialog {

    private final JTextField hostField = new JTextField("127.0.0.1:" + Protocol.DEFAULT_PORT);
    private final JTextField nicknameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmField = new JPasswordField();
    private final JComponent confirmRow;
    private final Component confirmGap = Box.createVerticalStrut(14);
    private final JLabel errorLabel = new JLabel(" ");
    private final FlatButton loginButton = new FlatButton("");
    private final JLabel switchLabel = new JLabel();
    private final BiConsumer<ServerConnection, String> onSuccess;
    /** true면 회원가입 화면, false면 로그인 화면 */
    private boolean signUpMode;

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
        styleField(passwordField);
        styleField(confirmField);
        hostField.addActionListener(e -> nicknameField.requestFocus());
        nicknameField.addActionListener(e -> passwordField.requestFocus());
        passwordField.addActionListener(e -> {
            if (signUpMode) confirmField.requestFocus();
            else attemptLogin();
        });
        confirmField.addActionListener(e -> attemptLogin());
        confirmRow = labeled("비밀번호 확인", confirmField);

        switchLabel.setFont(Theme.font(12));
        switchLabel.setForeground(Theme.PRIMARY_DARK);
        switchLabel.setHorizontalAlignment(SwingConstants.CENTER);
        switchLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        switchLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (loginButton.isEnabled()) setSignUpMode(!signUpMode);
            }
        });

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
        card.add(Box.createVerticalStrut(14));
        card.add(labeled("비밀번호 (" + Protocol.PASSWORD_MIN_LENGTH + "~" + Protocol.PASSWORD_MAX_LENGTH + "자)", passwordField));
        card.add(confirmGap);
        card.add(confirmRow);
        card.add(Box.createVerticalStrut(8));
        card.add(left(errorLabel));
        card.add(Box.createVerticalStrut(8));
        card.add(loginButton);
        card.add(Box.createVerticalStrut(12));
        card.add(center(switchLabel));

        root.add(card);
        setContentPane(root);
        setResizable(false);
        setSignUpMode(false);
        setLocationRelativeTo(null);
        nicknameField.requestFocusInWindow();
    }

    /** 로그인 화면 ↔ 회원가입 화면 전환 (비밀번호 확인 칸만 보이거나 숨겨진다) */
    private void setSignUpMode(boolean signUp) {
        signUpMode = signUp;
        setTitle(signUp ? "PlayTalk 회원가입" : "PlayTalk 로그인");
        confirmRow.setVisible(signUp);
        confirmGap.setVisible(signUp);
        confirmField.setText("");
        loginButton.setText(idleButtonText());
        switchLabel.setText(signUp
                ? "<html>이미 계정이 있나요? <u>로그인</u></html>"
                : "<html>처음이신가요? <u>회원가입</u></html>");
        errorLabel.setText(" ");
        setSize(460, signUp ? 640 : 570);
    }

    private String idleButtonText() {
        return signUpMode ? "가입하고 입장하기" : "로그인 & 입장하기";
    }

    private void attemptLogin() {
        String nickname = nicknameField.getText().trim();
        String nickError = Protocol.validateNickname(nickname);
        if (nickError != null) {
            showError(nickError);
            return;
        }
        String password = new String(passwordField.getPassword());
        String pwError = Protocol.validatePassword(password);
        if (pwError != null) {
            showError(pwError);
            passwordField.requestFocus();
            return;
        }
        if (signUpMode && !password.equals(new String(confirmField.getPassword()))) {
            showError("비밀번호 확인이 일치하지 않습니다.");
            confirmField.selectAll();
            confirmField.requestFocus();
            return;
        }
        boolean register = signUpMode;
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
                    if (msg.get("reason").contains("비밀번호")) {
                        passwordField.selectAll();
                        passwordField.requestFocus();
                    } else {
                        nicknameField.selectAll();
                        nicknameField.requestFocus();
                    }
                });
                // 회원가입: 가입에 성공하면 같은 연결로 바로 로그인
                conn.on(MessageType.REGISTER_OK, msg -> conn.send(Message.of(MessageType.LOGIN)
                        .put("nickname", nickname).put("password", password)));
                conn.on(MessageType.REGISTER_FAIL, msg -> {
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
                conn.send(Message.of(register ? MessageType.REGISTER : MessageType.LOGIN)
                        .put("nickname", nickname).put("password", password));
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        loginButton.setText(busy ? "접속 중..." : idleButtonText());
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
