package playtalk.client;

import playtalk.client.ui.LoginDialog;
import playtalk.client.ui.MainFrame;
import playtalk.client.ui.Theme;

import javax.swing.SwingUtilities;

/**
 * 클라이언트 실행 진입점.
 * <pre>
 *   java -cp target/classes playtalk.client.ClientMain
 * </pre>
 */
public class ClientMain {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            showLogin();
        });
    }

    /** 로그인 화면을 띄운다 (프로그램 시작, 로그아웃, 회원 탈퇴 후) */
    public static void showLogin() {
        new LoginDialog((connection, nickname) -> new MainFrame(connection, nickname).setVisible(true))
                .setVisible(true);
    }
}
