package playtalk.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 서버와 클라이언트가 주고받는 메시지 한 건.
 *
 * <p>네트워크에서는 한 줄(line)로 전송된다.
 * <pre>
 *   TYPE\tkey1=value1\tkey2=value2\n
 * </pre>
 * 값 안의 역슬래시, 탭, 줄바꿈은 이스케이프되므로 채팅 내용에 어떤 문자가 들어가도 안전하다.
 *
 * <p>표 형태 데이터(방 목록, 참가자 목록 등)는 {@link #putTable}/{@link #getTable}로 다룬다.
 * 새 기능이 필요하면 {@link MessageType}에 타입을 추가하고 필요한 key만 정하면 된다.
 */
public final class Message {

    /** 표 데이터에서 행(row) 구분자 */
    private static final char ROW_SEP = '\u001E';
    /** 표 데이터에서 열(column) 구분자 */
    private static final char COL_SEP = '\u001F';

    private final MessageType type;
    private final Map<String, String> fields = new LinkedHashMap<>();

    private Message(MessageType type) {
        this.type = type;
    }

    public static Message of(MessageType type) {
        return new Message(type);
    }

    public MessageType getType() {
        return type;
    }

    // ------------------------------------------------------------------ builder

    public Message put(String key, Object value) {
        fields.put(key, value == null ? "" : String.valueOf(value));
        return this;
    }

    public Message putList(String key, List<String> values) {
        List<String[]> rows = new ArrayList<>();
        for (String v : values) {
            rows.add(new String[]{v});
        }
        return putTable(key, rows);
    }

    public Message putTable(String key, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) sb.append(ROW_SEP);
            String[] row = rows.get(r);
            for (int c = 0; c < row.length; c++) {
                if (c > 0) sb.append(COL_SEP);
                sb.append(stripSeparators(row[c]));
            }
        }
        fields.put(key, sb.toString());
        return this;
    }

    // ------------------------------------------------------------------ getters

    public String get(String key) {
        return fields.getOrDefault(key, "");
    }

    public String get(String key, String defaultValue) {
        String v = fields.get(key);
        return v == null || v.isEmpty() ? defaultValue : v;
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(fields.get(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(fields.get(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(fields.get(key));
    }

    public boolean has(String key) {
        return fields.containsKey(key);
    }

    public List<String> getList(String key) {
        List<String> out = new ArrayList<>();
        for (String[] row : getTable(key)) {
            out.add(row.length > 0 ? row[0] : "");
        }
        return out;
    }

    public List<String[]> getTable(String key) {
        String raw = fields.get(key);
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<String[]> rows = new ArrayList<>();
        for (String row : raw.split(String.valueOf(ROW_SEP), -1)) {
            rows.add(row.split(String.valueOf(COL_SEP), -1));
        }
        return rows;
    }

    public Map<String, String> fields() {
        return Collections.unmodifiableMap(fields);
    }

    // ------------------------------------------------------------------ encode / decode

    /** 한 줄 문자열로 직렬화 (끝의 줄바꿈 제외). */
    public String encode() {
        StringBuilder sb = new StringBuilder(type.name());
        for (Map.Entry<String, String> e : fields.entrySet()) {
            sb.append('\t').append(escape(e.getKey())).append('=').append(escape(e.getValue()));
        }
        return sb.toString();
    }

    /**
     * 한 줄 문자열을 메시지로 복원한다.
     *
     * @throws IllegalArgumentException 알 수 없는 타입이거나 형식이 잘못된 경우
     */
    public static Message decode(String line) {
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException("빈 메시지");
        }
        String[] parts = line.split("\t", -1);
        Message m = new Message(MessageType.valueOf(parts[0].trim()));
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq < 0) continue;
            m.fields.put(unescape(parts[i].substring(0, eq)), unescape(parts[i].substring(eq + 1)));
        }
        return m;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 't' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    default -> sb.append(next);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String stripSeparators(String s) {
        if (s == null) return "";
        return s.replace(ROW_SEP, ' ').replace(COL_SEP, ' ');
    }

    @Override
    public String toString() {
        return encode();
    }
}
