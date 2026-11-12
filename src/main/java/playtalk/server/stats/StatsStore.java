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

    public StatsStore(Path file) {
        this.file = file;
        load();
    }

    public synchronized void record(GameType type, GameResult result) {
        result.outcomes().forEach((nick, outcome) -> {
            int[] row = stats.computeIfAbsent(nick, k -> new EnumMap<>(GameType.class))
                    .computeIfAbsent(type, k -> new int[3]);
            switch (outcome) {
                case Protocol.OUTCOME_WIN -> row[WIN]++;
                case Protocol.OUTCOME_LOSS -> row[LOSS]++;
                default -> row[DRAW]++;
            }
        });
        save();
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
