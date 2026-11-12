package playtalk.client.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

/**
 * 색상, 글꼴 등 디자인 값 모음 (Stitch 프로토타입 기준).
 * Figma/Stitch 디자인이 바뀌면 이 파일만 수정하면 된다.
 */
public final class Theme {

    private Theme() {
    }

    // ---------------------------------------------------------------- colors
    public static final Color PRIMARY = new Color(0x2563EB);
    public static final Color PRIMARY_DARK = new Color(0x004AC6);
    public static final Color PRIMARY_SOFT = new Color(0xDBE1FF);

    public static final Color TITLE_BAR = new Color(0x213145);
    public static final Color TITLE_TEXT = new Color(0xEAF1FF);
    public static final Color TITLE_MUTED = new Color(0x9AA6BC);

    public static final Color BACKGROUND = new Color(0xF8F9FF);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_LOW = new Color(0xEFF4FF);
    public static final Color SURFACE_MID = new Color(0xE5EEFF);
    public static final Color SURFACE_HIGH = new Color(0xDCE9FF);

    public static final Color TEXT = new Color(0x0B1C30);
    public static final Color TEXT_MUTED = new Color(0x434655);
    public static final Color TEXT_SUBTLE = new Color(0x737686);
    public static final Color BORDER = new Color(0xC3C6D7);
    public static final Color BORDER_SOFT = new Color(0xDDE3F0);

    public static final Color SUCCESS = new Color(0x007D55);
    public static final Color SUCCESS_SOFT = new Color(0x6FFBBE);
    public static final Color WARNING = new Color(0xD97706);
    public static final Color ERROR = new Color(0xBA1A1A);
    public static final Color ERROR_SOFT = new Color(0xFFDAD6);

    /** 아바타 배경색 (닉네임별로 고정) */
    private static final Color[] AVATAR_COLORS = {
            new Color(0x2563EB), new Color(0x007D55), new Color(0x7C3AED),
            new Color(0xDB2777), new Color(0xD97706), new Color(0x0891B2), new Color(0x565E74)};

    public static Color avatarColor(String nickname) {
        return AVATAR_COLORS[Math.floorMod(nickname.hashCode(), AVATAR_COLORS.length)];
    }

    // ---------------------------------------------------------------- fonts
    private static final String FONT_FAMILY = pickFontFamily(
            "Pretendard", "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo", "NanumGothic", "Noto Sans CJK KR");

    public static Font font(int size) {
        return new Font(FONT_FAMILY, Font.PLAIN, size);
    }

    public static Font bold(int size) {
        return new Font(FONT_FAMILY, Font.BOLD, size);
    }

    private static String pickFontFamily(String... candidates) {
        List<String> available = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String c : candidates) {
            if (available.contains(c)) return c;
        }
        return Font.DIALOG;
    }

    // ---------------------------------------------------------------- helpers
    public static Border padding(int v, int h) {
        return BorderFactory.createEmptyBorder(v, h, v, h);
    }

    /** 앱 시작 시 한 번 호출: 기본 Swing 컴포넌트 글꼴/색 통일 */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Font base = font(13);
        for (String key : new String[]{"Label.font", "Button.font", "TextField.font", "TextArea.font",
                "TextPane.font", "List.font", "ComboBox.font", "Spinner.font", "OptionPane.messageFont",
                "OptionPane.buttonFont", "Table.font", "TableHeader.font", "CheckBox.font", "RadioButton.font",
                "ToolTip.font", "PasswordField.font", "TitledBorder.font", "ProgressBar.font"}) {
            UIManager.put(key, base);
        }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("Panel.background", SURFACE);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ProgressBar.foreground", PRIMARY);
        UIManager.put("ProgressBar.background", SURFACE_HIGH);
        UIManager.put("ProgressBar.border", BorderFactory.createEmptyBorder());
    }
}
