package playtalk.server.game.games;

import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.server.Participant;
import playtalk.server.game.GameContext;
import playtalk.server.game.GameResult;
import playtalk.server.game.GameSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 가위바위보 (1:1). 두 사람이 모두 고르면 서버가 판정한다.
 * 제한 시간 안에 고르지 않으면 패배(둘 다 안 고르면 무승부).
 *
 * <pre>
 * GAME_START : limit(초)
 * GAME_ACTION: choice(ROCK/PAPER/SCISSORS)
 * GAME_STATE : event=CHOSEN, nickname        (누가 골랐는지만 공개)
 *              event=REVEAL, choices(표: nickname, choice)
 * </pre>
 */
public class RockPaperScissorsGame extends GameSession {

    public static final int TIME_LIMIT_SECONDS = 10;

    public enum Choice {
        ROCK("바위"), PAPER("보"), SCISSORS("가위");

        final String label;

        Choice(String label) {
            this.label = label;
        }

        boolean beats(Choice other) {
            return (this == ROCK && other == SCISSORS)
                    || (this == PAPER && other == ROCK)
                    || (this == SCISSORS && other == PAPER);
        }
    }

    private final Map<Participant, Choice> choices = new HashMap<>();
    private GameContext.Cancellable timer;

    public RockPaperScissorsGame(String id, List<Participant> players, GameContext context) {
        super(id, GameType.ROCK_PAPER_SCISSORS, players, context);
    }

    @Override
    public void fillStartMessage(Message start) {
        start.put("limit", TIME_LIMIT_SECONDS);
    }

    @Override
    public void start() {
        timer = schedule(this::resolve, TIME_LIMIT_SECONDS * 1000L);
    }

    @Override
    public void onAction(Participant from, Message action) {
        if (choices.containsKey(from)) {
            reject(from, "이미 선택했습니다.");
            return;
        }
        Choice choice;
        try {
            choice = Choice.valueOf(action.get("choice"));
        } catch (IllegalArgumentException e) {
            reject(from, "가위, 바위, 보 중에서 선택하세요.");
            return;
        }
        choices.put(from, choice);
        broadcast(state().put("event", "CHOSEN").put("nickname", from.nickname()));
        if (choices.size() == players.size()) {
            if (timer != null) timer.cancel();
            resolve();
        }
    }

    private void resolve() {
        if (isFinished()) return;
        Participant a = players.get(0);
        Participant b = players.get(1);
        Choice ca = choices.get(a);
        Choice cb = choices.get(b);

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{a.nickname(), ca == null ? "NONE" : ca.name()});
        rows.add(new String[]{b.nickname(), cb == null ? "NONE" : cb.name()});
        broadcast(state().put("event", "REVEAL").putTable("choices", rows));

        String vs = label(a, ca) + " vs " + label(b, cb) + " → ";
        GameResult result = new GameResult();
        if (ca == cb) {
            result.draw(a.nickname()).draw(b.nickname()).summary(vs + "무승부!");
        } else if (cb == null || (ca != null && ca.beats(cb))) {
            result.win(a.nickname()).loss(b.nickname()).summary(vs + a.nickname() + " 님 승리!");
        } else {
            result.win(b.nickname()).loss(a.nickname()).summary(vs + b.nickname() + " 님 승리!");
        }
        finish(result);
    }

    private static String label(Participant p, Choice c) {
        return p.nickname() + "(" + (c == null ? "시간 초과" : c.label) + ")";
    }
}
