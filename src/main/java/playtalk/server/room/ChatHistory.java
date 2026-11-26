package playtalk.server.room;

import playtalk.common.Protocol;
import playtalk.server.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
 *   <li>파일: 모든 대화를 data/chat/room{번호}.log 에 계속 이어 적는다.
 *       채팅방이 저장되어 번호가 유지되므로, 서버를 다시 켜면 이 파일에서 최근 대화를 다시 불러온다.</li>
 * </ul>
 * 모든 메서드는 동기화되어 있다.
 */
public class ChatHistory {

    private static final int MEMORY_LIMIT = 200;

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

    /** 채팅 한 줄 저장. 붙은 번호(seq)를 돌려준다 */
    public synchronized long add(int roomId, String roomTitle, String from, String text, String time) {
        RoomLog log = log(roomId);
        Entry e = new Entry(++log.lastSeq, from, text, time);
        log.entries.addLast(e);
        while (log.entries.size() > MEMORY_LIMIT) log.entries.removeFirst();
        appendToFile(roomId, roomTitle, log, e);
        return e.seq();
    }

    /** 지금까지 저장된 마지막 번호 (없으면 0) */
    public synchronized long lastSeq(int roomId) {
        return log(roomId).lastSeq;
    }

    /** seq가 upTo 이하인 대화 중 최근 limit개 (오래된 것부터) */
    public synchronized List<Entry> recent(int roomId, long upTo, int limit) {
        RoomLog log = log(roomId);
        List<Entry> out = new ArrayList<>();
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

    private Path fileOf(int roomId) {
        return dir.resolve("room" + roomId + ".log");
    }

    /** 메모리에 없으면 파일에서 최근 대화를 불러온다 */
    private RoomLog log(int roomId) {
        RoomLog log = logs.get(roomId);
        if (log != null) return log;
        log = new RoomLog();
        Path file = fileOf(roomId);
        if (Files.exists(file)) {
            try {
                for (String line : Files.readAllLines(file, Protocol.CHARSET)) {
                    String[] f = line.split("\t", 4); // seq, 시각, 보낸 사람, 내용
                    if (f.length < 4) continue;
                    try {
                        Entry e = new Entry(Long.parseLong(f[0]), RoomStore.unescape(f[2]),
                                RoomStore.unescape(f[3]), RoomStore.unescape(f[1]));
                        log.entries.addLast(e);
                        log.lastSeq = Math.max(log.lastSeq, e.seq());
                        while (log.entries.size() > MEMORY_LIMIT) log.entries.removeFirst();
                    } catch (NumberFormatException ignored) {
                    }
                }
            } catch (IOException ex) {
                Log.error("채팅 기록 읽기 실패: %s", ex);
            }
        }
        logs.put(roomId, log);
        return log;
    }

    /** 대화 파일이 있는 가장 큰 방 번호 (없으면 0) */
    public synchronized int maxRoomId() {
        int max = 0;
        if (!Files.isDirectory(dir)) return 0;
        try (var files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String n = p.getFileName().toString();
                if (n.startsWith("room") && n.endsWith(".log")) {
                    try {
                        max = Math.max(max, Integer.parseInt(n.substring(4, n.length() - 4)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return max;
    }

    private void appendToFile(int roomId, String roomTitle, RoomLog log, Entry e) {
        try {
            Files.createDirectories(dir);
            if (log.file == null) log.file = fileOf(roomId);
            try (BufferedWriter w = Files.newBufferedWriter(log.file, Protocol.CHARSET,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(e.seq() + "\t" + RoomStore.escape(e.time()) + "\t" + RoomStore.escape(e.from())
                        + "\t" + RoomStore.escape(e.text()) + "\n");
            }
        } catch (IOException ex) {
            Log.error("채팅 기록 저장 실패: %s", ex);
        }
    }
}
