package playtalk.server.stats;

import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.common.MessageType;
import playtalk.common.Protocol;
import playtalk.server.Log;
import playtalk.server.game.GameResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 사용자별 게임 전적. 서버를 다시 켜도 유지되도록 CSV 파일에 저장한다.
 *
 * <pre>
 * data/stats.csv
 * nickname,game,win,loss,draw
 * Alex,TIC_TAC_TOE,3,1,0
 * </pre>
 */
public class StatsStore {

    private static final int WIN = 0, LOSS = 1, DRAW = 2;

    private final Path file;
    private final Map<String, Map<GameType, int[]>> stats = new HashMap<>();
    /** roomId → 닉네임 → [승, 패, 무]. 채팅방별 전적은 서버가 켜져 있는 동안만 보관한다. */
    private final Map<Integer, Map<String, int[]>> roomStats = new HashMap<>();

    public StatsStore(Path file) {
        this.file = file;
        load();
    }

    public synchronized void record(GameType type, GameResult result, int roomId) {
        result.outcomes().forEach((nick, outcome) -> {
            int[] roomRow = roomStats.computeIfAbsent(roomId, k -> new LinkedHashMap<>())
                    .computeIfAbsent(nick, k -> new int[3]);
            roomRow[outcomeIndex(outcome)]++;
            int[] row = stats.computeIfAbsent(nick, k -> new EnumMap<>(GameType.class))
                    .computeIfAbsent(type, k -> new int[3]);
            row[outcomeIndex(outcome)]++;
        });
        save();
    }

    private static int outcomeIndex(String outcome) {
        return switch (outcome) {
            case Protocol.OUTCOME_WIN -> WIN;
            case Protocol.OUTCOME_LOSS -> LOSS;
            default -> DRAW;
        };
    }

    /** ROOM_STATS 응답 메시지 생성: 그 채팅방에서 한 게임의 사용자별 승/패/무 */
    public synchronized Message roomStatsMessage(int roomId) {
        List<String[]> rows = new ArrayList<>();
        roomStats.getOrDefault(roomId, Map.of()).forEach((nick, r) ->
                rows.add(new String[]{nick, String.valueOf(r[WIN]), String.valueOf(r[LOSS]), String.valueOf(r[DRAW])}));
        return Message.of(MessageType.ROOM_STATS).put("roomId", roomId).putTable("users", rows);
    }

    /** STATS 응답 메시지 생성 */
    public synchronized Message statsMessage(String nickname) {
        int[] total = new int[3];
        List<String[]> rows = new ArrayList<>();
        Map<GameType, int[]> byGame = stats.getOrDefault(nickname, Map.of());
        for (GameType type : GameType.values()) {
            int[] r = byGame.getOrDefault(type, new int[3]);
            for (int i = 0; i < 3; i++) total[i] += r[i];
            rows.add(new String[]{type.name(), String.valueOf(r[WIN]), String.valueOf(r[LOSS]), String.valueOf(r[DRAW])});
        }
        return Message.of(MessageType.STATS)
                .put("nickname", nickname)
                .put("win", total[WIN]).put("loss", total[LOSS]).put("draw", total[DRAW])
                .putTable("games", rows);
    }

    /** RANKING 응답 메시지 생성: 사용자별 전체 승/패/무 (순서는 승리 횟수 내림차순) */
    public synchronized Message rankingMessage() {
        List<String[]> rows = new ArrayList<>();
        for (var user : stats.entrySet()) {
            int[] total = new int[3];
            for (int[] r : user.getValue().values()) {
                for (int i = 0; i < 3; i++) total[i] += r[i];
            }
            if (total[WIN] + total[LOSS] + total[DRAW] == 0) continue;
            rows.add(new String[]{user.getKey(), String.valueOf(total[WIN]),
                    String.valueOf(total[LOSS]), String.valueOf(total[DRAW])});
        }
        rows.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));
        return Message.of(MessageType.RANKING).putTable("users", rows);
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file, Protocol.CHARSET)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",");
                if (f.length != 5 || f[0].equals("nickname")) continue;
                GameType type = GameType.parse(f[1]);
                if (type == null) continue;
                stats.computeIfAbsent(f[0], k -> new EnumMap<>(GameType.class))
                        .put(type, new int[]{Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4])});
            }
            Log.info("전적 파일 불러옴: %s (%d명)", file, stats.size());
        } catch (IOException | NumberFormatException e) {
            Log.error("전적 파일을 읽지 못했습니다: %s", e);
        }
    }

    /** 회원 탈퇴: 그 사용자의 전적을 지운다 (랭킹에서도 사라짐) */
    public synchronized void removeUser(String nickname) {
        if (stats.remove(nickname) != null) save();
        for (Map<String, int[]> room : roomStats.values()) room.remove(nickname);
    }

    private void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, Protocol.CHARSET)) {
                w.write("nickname,game,win,loss,draw\n");
                for (var user : stats.entrySet()) {
                    for (var game : user.getValue().entrySet()) {
                        int[] r = game.getValue();
                        w.write(user.getKey() + "," + game.getKey().name() + "," + r[WIN] + "," + r[LOSS] + "," + r[DRAW] + "\n");
                    }
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.error("전적 저장 실패: %s", e);
        }
    }
}
