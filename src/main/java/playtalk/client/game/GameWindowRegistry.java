package playtalk.client.game;

import playtalk.client.game.games.RpsWindow;
import playtalk.client.game.games.TicTacToeWindow;
import playtalk.client.game.games.TimingWindow;
import playtalk.common.GameType;

import javax.swing.JFrame;
import java.util.EnumMap;
import java.util.Map;

/** GameType → 게임 창 생성기. 새 게임 창을 만들면 여기에 한 줄 추가. */
public final class GameWindowRegistry {

    @FunctionalInterface
    public interface Factory {
        GameWindow create(JFrame owner, GameSessionInfo session);
    }

    private static final Map<GameType, Factory> FACTORIES = new EnumMap<>(GameType.class);

    static {
        FACTORIES.put(GameType.TIC_TAC_TOE, TicTacToeWindow::new);
        FACTORIES.put(GameType.ROCK_PAPER_SCISSORS, RpsWindow::new);
        FACTORIES.put(GameType.TIMING, TimingWindow::new);
    }

    private GameWindowRegistry() {
    }

    public static GameWindow create(JFrame owner, GameSessionInfo session) {
        Factory f = FACTORIES.get(session.type());
        if (f == null) throw new IllegalArgumentException("게임 창이 등록되지 않았습니다: " + session.type());
        return f.create(owner, session);
    }
}
