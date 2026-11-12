package playtalk.server.game;

import playtalk.common.Protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 게임 한 판의 결과: 사용자별 승/패/무와 한 줄 요약. */
public class GameResult {

    private final Map<String, String> outcomes = new LinkedHashMap<>();
    private String summary = "";

    public GameResult win(String nickname) {
        outcomes.put(nickname, Protocol.OUTCOME_WIN);
        return this;
    }

    public GameResult loss(String nickname) {
        outcomes.put(nickname, Protocol.OUTCOME_LOSS);
        return this;
    }

    public GameResult draw(String nickname) {
        outcomes.put(nickname, Protocol.OUTCOME_DRAW);
        return this;
    }

    public GameResult summary(String summary) {
        this.summary = summary;
        return this;
    }

    public Map<String, String> outcomes() {
        return Collections.unmodifiableMap(outcomes);
    }

    public String summary() {
        return summary;
    }
}
