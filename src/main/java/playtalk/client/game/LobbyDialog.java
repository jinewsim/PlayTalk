package playtalk.client.game;

import playtalk.client.net.ServerConnection;
import playtalk.client.ui.Theme;
import playtalk.client.ui.components.Avatar;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.GameType;
import playtalk.common.Message;
import playtalk.common.MessageType;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/** 다인용 게임 대기실: 참가/나가기, 호스트는 시작. */
public class LobbyDialog extends JDialog {

    private final ServerConnection connection;
    private final String me;
    private final JLabel title = new JLabel();
    private final JLabel info = new JLabel();
    private final DefaultListModel<String> players = new DefaultListModel<>();
    private final FlatButton join = new FlatButton("참가하기");
    private final FlatButton leave = new FlatButton("나가기", FlatButton.Style.GHOST);
    private final FlatButton start = new FlatButton("게임 시작");
    private String lobbyId;

    public LobbyDialog(JFrame owner, ServerConnection connection, String me) {
        super(owner, "다인용 게임 대기실", false);
        this.connection = connection;
        this.me = me;

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.SURFACE);
        root.setBorder(Theme.padding(18, 20));
        title.setFont(Theme.bold(18));
        title.setForeground(Theme.TEXT);
        info.setFont(Theme.font(12));
        info.setForeground(Theme.TEXT_SUBTLE);
        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(title, BorderLayout.NORTH);
        head.add(info, BorderLayout.SOUTH);

        JList<String> list = new JList<>(players);
        list.setFixedCellHeight(40);
        list.setBackground(Theme.SURFACE_LOW);
        list.setCellRenderer((l, nick, i, sel, f) -> {
            JLabel lb = new JLabel(nick + (nick.equals(me) ? " (나)" : ""), new Avatar(nick, 28), JLabel.LEFT);
            lb.setIconTextGap(10);
            lb.setFont(Theme.font(14));
            lb.setBorder(Theme.padding(4, 10));
            return lb;
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);

        join.addActionListener(e -> connection.send(Message.of(MessageType.LOBBY_JOIN).put("lobbyId", lobbyId)));
        leave.addActionListener(e -> {
            connection.send(Message.of(MessageType.LOBBY_LEAVE).put("lobbyId", lobbyId));
            setVisible(false);
        });
        start.addActionListener(e -> connection.send(Message.of(MessageType.LOBBY_START).put("lobbyId", lobbyId)));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(leave);
        buttons.add(join);
        buttons.add(start);

        root.add(head, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(380, 400);
        setLocationRelativeTo(owner);
    }

    /** LOBBY_STATE 수신 */
    public void update(Message state) {
        lobbyId = state.get("lobbyId");
        GameType type = GameType.parse(state.get("game"));
        String host = state.get("host");
        List<String> names = state.getList("players");
        int min = state.getInt("min", 2), max = state.getInt("max", 5);

        title.setText("[" + (type == null ? state.get("game") : type.displayName()) + "] 대기실");
        info.setText("호스트 " + host + " · " + names.size() + "/" + max + "명 · 최소 " + min + "명");
        players.clear();
        names.forEach(players::addElement);

        boolean joined = names.contains(me);
        boolean isHost = host.equals(me);
        join.setVisible(!joined);
        join.setEnabled(names.size() < max);
        leave.setText(isHost ? "대기실 닫기" : "나가기");
        leave.setVisible(joined);
        start.setVisible(isHost);
        start.setEnabled(names.size() >= min);
    }

    public String getLobbyId() {
        return lobbyId;
    }
}
