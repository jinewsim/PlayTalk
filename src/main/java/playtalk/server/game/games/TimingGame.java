package playtalk.server.game.games;

import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.server.Participant;
import playtalk.server.game.GameContext;
import playtalk.server.game.GameResult;
import playtalk.server.game.GameSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시간 맞추기 (다인용 2~5명).
 * 카운트다운 후 타이머가 시작되고, 목표 시간(10초)에 가장 가깝게 STOP을 누른 사람이 승리한다.
 * 타이머는 처음 몇 초만 보이고 이후에는 숨겨진다.
 *
 * <p>시간 측정은 서버 기준이다(공정성). 네트워크 지연이 조금 포함될 수 있다.
 *
 * <pre>
 * GAME_START : target(ms), visibleMs, countdown(초)
 * GAME_STATE : event=COUNTDOWN, count
 *              event=RUNNING
 *              event=STOPPED, nickname                 (기록은 끝날 때 공개)
 *              event=REVEAL, records(표: nickname, elapsedMs, diffMs) — 기록 없으면 -1
 * GAME_ACTION: action=STOP
 * </pre>
 */
public class TimingGame extends GameSession {

    public static final long TARGET_MS = 10_000;
    public static final long VISIBLE_MS = 3_000;
    public static final int COUNTDOWN = 3;
    /** 목표 시간 + 이 시간이 지나도록 안 누르면 기록 없음 처리 */
    public static final long GRACE_MS = 5_000;

    private final Map<Participant, Long> records = new LinkedHashMap<>();
    private long startedAt;
    private boolean running;
    private GameContext.Cancellable timeout;

    public TimingGame(String id, List<Participant> players, GameContext context) {
        super(id, GameType.TIMING, players, context);
    }

    @Override
    public void fillStartMessage(Message start) {
        start.put("target", TARGET_MS).put("visibleMs", VISIBLE_MS).put("countdown", COUNTDOWN);
    }

    @Override
    public void start() {
        for (int i = 0; i < COUNTDOWN; i++) {
            final int count = COUNTDOWN - i;
            schedule(() -> broadcast(state().put("event", "COUNTDOWN").put("count", count)), i * 1000L);
        }
        schedule(this::begin, COUNTDOWN * 1000L);
    }

    private void begin() {
        running = true;
        startedAt = System.nanoTime();
        broadcast(state().put("event", "RUNNING"));
        timeout = schedule(this::resolve, TARGET_MS + GRACE_MS);
    }

    @Override
    public void onAction(Participant from, Message action) {
        if (!"STOP".equals(action.get("action"))) return;
        if (!running) {
            reject(from, "아직 시작하지 않았습니다.");
            return;
        }
        if (records.containsKey(from) || !active.contains(from)) return;
        records.put(from, (System.nanoTime() - startedAt) / 1_000_000);
        broadcast(state().put("event", "STOPPED").put("nickname", from.nickname()));
        checkAllStopped();
    }

    @Override
    protected void onPlayerDropped(Participant leaver) {
        checkAllStopped();
    }

    private void checkAllStopped() {
        for (Participant p : active) {
            if (!records.containsKey(p)) return;
        }
        if (timeout != null) timeout.cancel();
        resolve();
    }

    private void resolve() {
        if (isFinished()) return;
        running = false;

        List<String[]> rows = new ArrayList<>();
        long best = Long.MAX_VALUE;
        for (Participant p : players) {
            Long t = records.get(p);
            long diff = t == null ? -1 : Math.abs(t - TARGET_MS);
            rows.add(new String[]{p.nickname(), String.valueOf(t == null ? -1 : t), String.valueOf(diff)});
            if (t != null && active.contains(p)) best = Math.min(best, diff);
        }
        rows.sort((r1, r2) -> Long.compare(rank(r1[2]), rank(r2[2])));
        broadcast(state().put("event", "REVEAL").putTable("records", rows));

        GameResult result = new GameResult();
        if (best == Long.MAX_VALUE) {
            for (Participant p : players) result.draw(p.nickname());
            finish(result.summary("아무도 기록을 남기지 않아 무승부입니다."));
            return;
        }
        List<String> winners = new ArrayList<>();
        for (Participant p : players) {
            Long t = records.get(p);
            if (t != null && active.contains(p) && Math.abs(t - TARGET_MS) == best) {
                result.win(p.nickname());
                winners.add(p.nickname());
            } else {
                result.loss(p.nickname());
            }
        }
        finish(result.summary(String.join(", ", winners) + " 님 승리! (오차 "
                + String.format("%.3f", best / 1000.0) + "초)"));
    }

    private static long rank(String diff) {
        long d = Long.parseLong(diff);
        return d < 0 ? Long.MAX_VALUE : d;
    }
}
