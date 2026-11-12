package playtalk.client.game.games;

import playtalk.client.game.GameSessionInfo;
import playtalk.client.game.GameWindow;
import playtalk.client.ui.Theme;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

/** 틱택토 3x3 게임판. 내 차례에만 빈칸을 누를 수 있다. */
public class TicTacToeWindow extends GameWindow {

    private final FlatButton[] cells = new FlatButton[9];
    private String board = ".........";
    private String turn;
    private String myMark = "";

    public TicTacToeWindow(JFrame owner, GameSessionInfo session) {
        super(owner, session, 380, 520);
        JPanel grid = new JPanel(new GridLayout(3, 3, 8, 8));
        grid.setOpaque(false);
        for (int i = 0; i < 9; i++) {
            final int cell = i;
            FlatButton b = new FlatButton("", FlatButton.Style.SECONDARY).radius(10);
            b.setFont(Theme.bold(36));
            b.setPreferredSize(new Dimension(86, 86));
            b.addActionListener(e -> {
                // 버튼을 비활성화하면 X/O 색이 회색으로 바뀌므로, 대신 클릭을 무시한다 (최종 검사는 서버가 함)
                if (!isEnded() && session.myNickname().equals(turn) && board.charAt(cell) == '.') {
                    session.send(session.action().put("cell", cell));
                }
            });
            cells[i] = b;
            grid.add(b);
        }
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(grid);
        content.add(center);
    }

    @Override
    public void onStart(Message start) {
        myMark = start.get("x").equals(session.myNickname()) ? "X" : "O";
        update(start.get("board"), start.get("turn"));
    }

    @Override
    public void onState(Message state) {
        if (state.has("board")) update(state.get("board"), state.get("turn"));
    }

    private void update(String board, String turn) {
        this.board = board;
        this.turn = turn;
        boolean myTurn = session.myNickname().equals(turn);
        for (int i = 0; i < 9; i++) {
            char c = board.charAt(i);
            cells[i].setText(c == '.' ? "" : String.valueOf(c));
            // 내가 둘 수 있는 빈칸만 진하게 표시
            cells[i].setStyle(myTurn && c == '.' ? FlatButton.Style.SECONDARY : FlatButton.Style.SOFT);
            cells[i].setForeground(c == 'X' ? Theme.PRIMARY : Theme.ERROR);
        }
        setStatus(myTurn ? "내 차례입니다 (" + myMark + ")" : turn + " 님의 차례입니다");
    }

    @Override
    protected void onGameEnded() {
        for (int i = 0; i < 9; i++) {
            cells[i].setStyle(FlatButton.Style.SOFT);
            cells[i].setForeground(board.charAt(i) == 'X' ? Theme.PRIMARY : Theme.ERROR);
        }
    }
}
