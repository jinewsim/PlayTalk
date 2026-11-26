package playtalk.server.room;

import playtalk.common.Protocol;
import playtalk.server.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 채팅방 목록 파일 (data/rooms.tsv). 서버를 껐다 켜도 만든 채팅방이 남아 있도록 한다.
 * 한 줄에 방 하나: id, 최대 인원, 방장, 제목 (탭으로 구분, 제목의 탭/줄바꿈은 \t \n으로 적는다).
 */
public class RoomStore {

    /** 파일에서 읽은 방 정보 */
    public record Saved(int id, int max, String owner, String title) {
    }

    private final Path file;

    public RoomStore(Path file) {
        this.file = file;
    }

    public synchronized List<Saved> load() {
        List<Saved> out = new ArrayList<>();
        if (file == null || !Files.exists(file)) return out;
        try {
            for (String line : Files.readAllLines(file, Protocol.CHARSET)) {
                String[] f = line.split("\t", 4);
                if (f.length < 4) continue;
                try {
                    out.add(new Saved(Integer.parseInt(f[0]), Integer.parseInt(f[1]), unescape(f[2]), unescape(f[3])));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            Log.error("채팅방 목록 읽기 실패: %s", e);
        }
        return out;
    }

    public synchronized void save(Collection<ChatRoom> rooms) {
        if (file == null) return;
        StringBuilder sb = new StringBuilder();
        for (ChatRoom r : rooms) {
            sb.append(r.getId()).append('\t').append(r.getMaxUsers()).append('\t')
                    .append(escape(r.getOwner())).append('\t').append(escape(r.getTitle())).append('\n');
        }
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), Protocol.CHARSET);
        } catch (IOException e) {
            Log.error("채팅방 목록 저장 실패: %s", e);
        }
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
