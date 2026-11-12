package playtalk.server.game.games;

import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.server.Participant;
import playtalk.server.game.GameContext;
import playtalk.server.game.GameResult;
import playtalk.server.game.GameSession;

import java.util.List;

/**
 * 틱택토 (1:1). 먼저 초대한 사람이 X로 선공.
 *
 * <pre>
 * GAME_START : x, o, board(9자리, '.' = 빈칸), turn
 * GAME_ACTION: cell(0~8)
 * GAME_STATE : board, turn, last(방금 둔 칸), lastBy
 * </pre>
 */
public class TicTacToeGame extends GameSession {

    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}};

    private final char[] board = ".........".toCharArray();
    private final Participant x;
    private final Participant o;
    private Participant turn;

    public TicTacToeGame(String id, List<Participant> players, GameContext context) {
        super(id, GameType.TIC_TAC_TOE, players, context);
        this.x = players.get(0);
        this.o = players.get(1);
        this.turn = x;
    }

    @Override
    public void fillStartMessage(Message start) {
        start.put("x", x.nickname()).put("o", o.nickname())
                .put("board", new String(board)).put("turn", turn.nickname());
    }

    @Override
    public void start() {
        // 초기 상태는 GAME_START에 포함되어 있음
    }

    @Override
    public void onAction(Participant from, Message action) {
        if (from != turn) {
            reject(from, "상대방의 차례입니다.");
            return;
        }
        int cell = action.getInt("cell", -1);
        if (cell < 0 || cell > 8 || board[cell] != '.') {
            reject(from, "둘 수 없는 칸입니다.");
            return;
        }
        char mark = from == x ? 'X' : 'O';
        board[cell] = mark;
        turn = from == x ? o : x;

        broadcast(state()
                .put("board", new String(board))
                .put("turn", turn.nickname())
                .put("last", cell)
                .put("lastBy", from.nickname()));

        if (isWinner(mark)) {
            Participant loser = from == x ? o : x;
            finish(new GameResult().win(from.nickname()).loss(loser.nickname())
                    .summary(from.nickname() + " 님이 한 줄을 완성해 승리했습니다!"));
        } else if (new String(board).indexOf('.') < 0) {
            finish(new GameResult().draw(x.nickname()).draw(o.nickname())
                    .summary("빈칸이 없어 무승부입니다."));
        }
    }

    private boolean isWinner(char mark) {
        for (int[] line : LINES) {
            if (board[line[0]] == mark && board[line[1]] == mark && board[line[2]] == mark) {
                return true;
            }
        }
        return false;
    }
}
