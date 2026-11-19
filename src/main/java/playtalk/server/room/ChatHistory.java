package playtalk.server.room;

import playtalk.common.Protocol;
import playtalk.server.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 채팅방별 대화 기록.
 * <ul>
 *   <li>메모리: 방이 있는 동안 최근 {@value #MEMORY_LIMIT}개를 보관한다. 방에 다시 들어온 사람이 불러올 수 있다.</li>
 *   <li>파일: 모든 대화를 data/chat/ 아래에 계속 이어 적는다 (서버를 꺼도 기록이 남는다).
 *       채팅방 번호는 서버를 켤 때마다 다시 매겨지므로 파일 이름에 만든 시각을 함께 쓴다.</li>
 * </ul>
 * 모든 메서드는 동기화되어 있다.
 */
public class ChatHistory {

    private static final int MEMORY_LIMIT = 200;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 저장된 채팅 한 줄. seq는 방마다 1부터 늘어나는 번호다. */
    public record Entry(long seq, String from, String text, String time) {
    }

    private static final class RoomLog {
        final Deque<Entry> entries = new ArrayDeque<>();
        long lastSeq;
        Path file;
    }

    private final Path dir;
    private final Map<Integer, RoomLog> logs = new HashMap<>();

    public ChatHistory(Path dir) {
        this.dir = dir;
    }

    /** 채팅 한 줄 저장 */
    public synchronized void add(int roomId, String roomTitle, String from, String text, String time) {
        RoomLog log = logs.computeIfAbsent(roomId, k -> new RoomLog());
        Entry e = new Entry(++log.lastSeq, from, text, time);
        log.entries.addLast(e);
        while (log.entries.size() > MEMORY_LIMIT) log.entries.removeFirst();
        appendToFile(roomId, roomTitle, log, e);
    }

    /** 지금까지 저장된 마지막 번호 (없으면 0) */
    public synchronized long lastSeq(int roomId) {
        RoomLog log = logs.get(roomId);
        return log == null ? 0 : log.lastSeq;
    }

    /** seq가 upTo 이하인 대화 중 최근 limit개 (오래된 것부터) */
    public synchronized List<Entry> recent(int roomId, long upTo, int limit) {
        RoomLog log = logs.get(roomId);
        List<Entry> out = new ArrayList<>();
        if (log == null) return out;
        for (Entry e : log.entries) {
            if (e.seq() <= upTo) out.add(e);
        }
        return out.size() <= limit ? out : new ArrayList<>(out.subList(out.size() - limit, out.size()));
    }

    /** 방이 삭제되면 메모리 기록을 버린다 (파일은 그대로 둔다) */
    public synchronized void closeRoom(int roomId) {
        logs.remove(roomId);
    }

    // ------------------------------------------------------------------ 파일

    private void appendToFile(int roomId, String roomTitle, RoomLog log, Entry e) {
        try {
            if (log.file == null) {
                Files.createDirectories(dir);
                log.file = dir.resolve(LocalDateTime.now().format(FILE_TIME) + "_room" + roomId + ".log");
                Files.writeString(log.file, "# 채팅방 #" + roomId + " " + roomTitle + "\n", Protocol.CHARSET,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            try (BufferedWriter w = Files.newBufferedWriter(log.file, Protocol.CHARSET,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(e.time() + "\t" + e.from() + "\t" + e.text().replace("\\", "\\\\")
                        .replace("\n", "\\n").replace("\t", "\\t") + "\n");
            }
        } catch (IOException ex) {
            Log.error("채팅 기록 저장 실패: %s", ex);
        }
    }
}
