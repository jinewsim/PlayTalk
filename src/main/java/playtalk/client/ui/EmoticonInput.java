package playtalk.client.ui;

import playtalk.common.Emoticons;

import javax.swing.Icon;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;

/**
 * 채팅 입력칸. 이모티콘을 고르면 {@code :smile:} 같은 코드 대신 이미지가 바로 보인다.
 * 보낼 때는 {@link #getMessageText()}로 이미지를 다시 코드로 바꿔서 보낸다 (프로토콜은 그대로).
 */
public class EmoticonInput extends JTextPane {

    /** 입력칸 안 이모티콘 크기 (글자 크기에 맞춤) */
    static final int ICON_SIZE = 18;
    private final int rows;

    public EmoticonInput(int rows) {
        this.rows = rows;
    }

    /** 커서 위치에 이모티콘 이미지를 넣는다 */
    public void insertEmoticon(String name) {
        Icon icon = EmoticonIcons.inline(name, ICON_SIZE);
        try {
            if (icon == null) { // 이미지가 없으면 코드 글자로
                replaceSelection(Emoticons.code(name));
                return;
            }
            replaceSelection("");
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            getStyledDocument().insertString(getCaretPosition(), " ", attrs);
        } catch (BadLocationException ignored) {
        }
    }

    /** 입력한 내용을 보낼 문자열로: 이모티콘 이미지는 :이름: 코드로 바꾼다 */
    public String getMessageText() {
        StyledDocument doc = getStyledDocument();
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = 0; i < doc.getLength(); i++) {
                Element el = doc.getCharacterElement(i);
                Icon icon = StyleConstants.getIcon(el.getAttributes());
                String name = icon == null ? null : nameOf(icon);
                sb.append(name != null ? Emoticons.code(name) : doc.getText(i, 1));
            }
        } catch (BadLocationException ignored) {
        }
        return sb.toString();
    }

    private static String nameOf(Icon icon) {
        for (String name : Emoticons.NAMES) {
            if (EmoticonIcons.inline(name, ICON_SIZE) == icon) return name;
        }
        return null;
    }

    /** JTextArea(rows, ..)처럼 기본 높이를 몇 줄로 고정한다 (더 길면 스크롤) */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        Font f = getFont();
        FontMetrics fm = getFontMetrics(f);
        Insets in = getInsets();
        return new Dimension(200, fm.getHeight() * rows + in.top + in.bottom);
    }
}
