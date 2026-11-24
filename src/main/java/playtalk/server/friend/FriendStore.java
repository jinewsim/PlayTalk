package playtalk.server.friend;

import playtalk.common.Protocol;
import playtalk.server.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 친구 관계와 대기 중인 친구 요청. 서버를 다시 켜도 유지되도록 CSV 파일에 저장한다.
 *
 * <pre>
 * data/friends.csv
 * type,a,b
 * FRIEND,Alex,민수      ← 서로 친구 (한 줄에 한 쌍)
 * REQUEST,지은,Alex     ← 지은 → Alex 요청 대기 중
 * </pre>
 *
 * <p>상대가 접속해 있지 않아도 요청을 보낼 수 있고, 상대는 다음에 로그인할 때 요청을 확인한다.
 */
public class FriendStore {

    /** 요청 결과 */
    public enum RequestResult { SENT, ACCEPTED_BOTH, ALREADY_FRIENDS, ALREADY_SENT }

    private final Path file;
    /** 닉네임 → 친구 닉네임들 (양쪽에 모두 저장) */
    private final Map<String, Set<String>> friends = new HashMap<>();
    /** 받는 사람 → 요청 보낸 사람들 */
    private final Map<String, Set<String>> received = new HashMap<>();
    /** 보낸 사람 → 요청 받은 사람들 */
    private final Map<String, Set<String>> sent = new HashMap<>();

    public FriendStore(Path file) {
        this.file = file;
        load();
    }

    /**
     * from이 to에게 친구 요청을 보낸다.
     * to가 이미 from에게 요청을 보내 둔 상태라면 바로 친구가 된다.
     */
    public synchronized RequestResult request(String from, String to) {
        if (areFriends(from, to)) return RequestResult.ALREADY_FRIENDS;
        if (setOf(received, from).contains(to)) {
            makeFriends(to, from);
            save();
            return RequestResult.ACCEPTED_BOTH;
        }
        if (!setOf(sent, from).add(to)) return RequestResult.ALREADY_SENT;
        setOf(received, to).add(from);
        save();
        return RequestResult.SENT;
    }

    /** @return 처리할 요청이 있었으면 true */
    public synchronized boolean reply(String me, String from, boolean accept) {
        if (!setOf(received, me).contains(from)) return false;
        if (accept) {
            makeFriends(from, me);
        } else {
            removeRequest(from, me);
        }
        save();
        return true;
    }

    /**
     * 친구 삭제. 친구가 아니고 내가 보낸 요청이 있으면 그 요청을 취소한다.
     *
     * @return 삭제하거나 취소한 것이 있으면 true
     */
    public synchronized boolean remove(String me, String target) {
        boolean changed = false;
        if (areFriends(me, target)) {
            setOf(friends, me).remove(target);
            setOf(friends, target).remove(me);
            changed = true;
        }
        if (setOf(sent, me).contains(target)) {
            removeRequest(me, target);
            changed = true;
        }
        if (changed) save();
        return changed;
    }

    public synchronized boolean areFriends(String a, String b) {
        return setOf(friends, a).contains(b);
    }

    public synchronized List<String> friendsOf(String nickname) {
        return sorted(friends.get(nickname));
    }

    public synchronized List<String> receivedBy(String nickname) {
        return sorted(received.get(nickname));
    }

    public synchronized List<String> sentBy(String nickname) {
        return sorted(sent.get(nickname));
    }

    // ------------------------------------------------------------------ internal

    private void makeFriends(String a, String b) {
        removeRequest(a, b);
        removeRequest(b, a);
        setOf(friends, a).add(b);
        setOf(friends, b).add(a);
    }

    private void removeRequest(String from, String to) {
        setOf(sent, from).remove(to);
        setOf(received, to).remove(from);
    }

    private static Set<String> setOf(Map<String, Set<String>> map, String key) {
        return map.computeIfAbsent(key, k -> new LinkedHashSet<>());
    }

    private static List<String> sorted(Set<String> set) {
        List<String> list = set == null ? new ArrayList<>() : new ArrayList<>(set);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file, Protocol.CHARSET)) {
            String line;
            int count = 0;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",");
                if (f.length != 3) continue;
                if (f[0].equals("FRIEND")) {
                    makeFriends(f[1], f[2]);
                    count++;
                } else if (f[0].equals("REQUEST")) {
                    setOf(sent, f[1]).add(f[2]);
                    setOf(received, f[2]).add(f[1]);
                }
            }
            Log.info("친구 파일 불러옴: %s (친구 %d쌍)", file, count);
        } catch (IOException e) {
            Log.error("친구 파일을 읽지 못했습니다: %s", e);
        }
    }

    private void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, Protocol.CHARSET)) {
                w.write("type,a,b\n");
                for (var e : friends.entrySet()) {
                    for (String other : e.getValue()) {
                        // 한 쌍을 한 번만 기록
                        if (e.getKey().compareTo(other) < 0) w.write("FRIEND," + e.getKey() + "," + other + "\n");
                    }
                }
                for (var e : sent.entrySet()) {
                    for (String to : e.getValue()) w.write("REQUEST," + e.getKey() + "," + to + "\n");
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.error("친구 저장 실패: %s", e);
        }
    }
}
