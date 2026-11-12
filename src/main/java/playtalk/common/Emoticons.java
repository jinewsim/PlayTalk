package playtalk.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 이모티콘 식별자.
 *
 * <p>메시지 본문에는 이미지 대신 {@code :smile:} 같은 코드가 들어간다.
 * 서버는 코드를 그대로 전달하고, 클라이언트가 코드 위치에 이미지를 그려 넣는다.
 * 그래서 텍스트와 이모티콘을 섞어 보내도 입력한 순서대로 한 말풍선에 표시된다.
 *
 * <p>새 이모티콘 추가: 이 목록에 이름을 넣고 src/main/resources/emoticons/이름.png 추가.
 */
public final class Emoticons {

    private Emoticons() {
    }

    public static final List<String> NAMES = List.of(
            "smile", "laugh", "wink", "sad", "angry", "surprised", "heart", "gg");

    private static final Pattern TOKEN = Pattern.compile(":([a-z0-9_]+):");

    public static String code(String name) {
        return ":" + name + ":";
    }

    /** 텍스트/이모티콘 조각. 이모티콘이면 emoticon에 이름, 아니면 text에 문자열. */
    public record Part(String text, String emoticon) {
        public boolean isEmoticon() {
            return emoticon != null;
        }
    }

    /** "안녕 :smile: 반가워" → [텍스트 "안녕 ", 이모티콘 smile, 텍스트 " 반가워"] */
    public static List<Part> split(String text) {
        List<Part> parts = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        int last = 0;
        while (m.find()) {
            if (!NAMES.contains(m.group(1))) continue;
            if (m.start() > last) parts.add(new Part(text.substring(last, m.start()), null));
            parts.add(new Part(null, m.group(1)));
            last = m.end();
        }
        if (last < text.length()) parts.add(new Part(text.substring(last), null));
        return parts;
    }
}
