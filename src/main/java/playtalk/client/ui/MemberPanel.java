package playtalk.client.ui;

import playtalk.client.ui.components.Avatar;
import playtalk.client.ui.components.FlatButton;
import playtalk.common.Message;
import playtalk.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;

/** 오른쪽 사이드바: 현재 방 참여자(상태, 1:1 대결 신청) + 전체 접속자 (디스코드의 멤버 목록 자리). */
public class MemberPanel extends JPanel {

    public record Member(String nickname, boolean inGame, boolean owner) {
    }

    private final String myNickname;
    private final DefaultListModel<Member> members = new DefaultListModel<>();
    private final JList<Member> memberList = new JList<>(members);
    private final DefaultListModel<String> online = new DefaultListModel<>();
    private final JLabel memberTitle = sectionTitle("방 참여자");
    private final JLabel onlineTitle = sectionTitle("전체 접속자");
    private final FlatButton challenge = new FlatButton("1:1 대결 신청");

    public MemberPanel(String myNickname, Consumer<String> onChallenge) {
        super(new GridLayout(2, 1, 0, 12));
        this.myNickname = myNickname;
        setBackground(Theme.SURFACE_LOW);
        setPreferredSize(new Dimension(230, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER_SOFT), Theme.padding(14, 12)));

        // ---- 방 참여자
        memberList.setCellRenderer(new MemberRenderer());
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        memberList.setBackground(Theme.SURFACE_LOW);
        memberList.setFixedCellHeight(48);
        memberList.addListSelectionListener(e -> updateChallengeButton());
        challenge.setEnabled(false);
        challenge.setToolTipText("목록에서 상대를 고른 뒤 누르세요");
        challenge.addActionListener(e -> {
            Member m = memberList.getSelectedValue();
            if (m != null) onChallenge.accept(m.nickname());
        });

        JPanel roomSection = new JPanel(new BorderLayout(0, 6));
        roomSection.setOpaque(false);
        roomSection.add(memberTitle, BorderLayout.NORTH);
        roomSection.add(scroll(memberList), BorderLayout.CENTER);
        roomSection.add(challenge, BorderLayout.SOUTH);

        // ---- 전체 접속자
        JList<String> onlineList = new JList<>(online);
        onlineList.setBackground(Theme.SURFACE_LOW);
        onlineList.setFixedCellHeight(32);
        onlineList.setCellRenderer((list, nick, index, sel, focus) -> {
            JLabel l = new JLabel(nick + (nick.equals(myNickname) ? " (나)" : ""), new Avatar(nick, 22, Theme.SUCCESS), JLabel.LEFT);
            l.setIconTextGap(8);
            l.setFont(Theme.font(13));
            l.setForeground(Theme.TEXT_MUTED);
            l.setBorder(Theme.padding(2, 4));
            return l;
        });
        onlineList.setFocusable(false);
        JPanel onlineSection = new JPanel(new BorderLayout(0, 6));
        onlineSection.setOpaque(false);
        onlineSection.add(onlineTitle, BorderLayout.NORTH);
        onlineSection.add(scroll(onlineList), BorderLayout.CENTER);

        add(roomSection);
        add(onlineSection);
    }

    /** ROOM_MEMBERS 수신 */
    public void setMembers(Message msg, int max) {
        String owner = msg.get("owner");
        String selected = memberList.getSelectedValue() == null ? null : memberList.getSelectedValue().nickname();
        members.clear();
        for (String[] r : msg.getTable("members")) {
            members.addElement(new Member(r[0], Protocol.STATUS_IN_GAME.equals(r[1]), r[0].equals(owner)));
        }
        memberTitle.setText("방 참여자  " + members.size() + "/" + max);
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).nickname().equals(selected)) memberList.setSelectedIndex(i);
        }
        updateChallengeButton();
    }

    public void clearMembers() {
        members.clear();
        memberTitle.setText("방 참여자");
        updateChallengeButton();
    }

    public boolean isMemberInGame(String nickname) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).nickname().equals(nickname)) return members.get(i).inGame();
        }
        return false;
    }

    /** USER_LIST 수신 */
    public void setOnlineUsers(List<String> users) {
        online.clear();
        users.forEach(online::addElement);
        onlineTitle.setText("전체 접속자  " + users.size());
    }

    private void updateChallengeButton() {
        Member m = memberList.getSelectedValue();
        challenge.setEnabled(m != null && !m.nickname().equals(myNickname) && !m.inGame());
    }

    private static JScrollPane scroll(JList<?> list) {
        JScrollPane s = new JScrollPane(list);
        s.setBorder(null);
        s.getViewport().setBackground(Theme.SURFACE_LOW);
        return s;
    }

    private static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(12));
        l.setForeground(Theme.TEXT_SUBTLE);
        l.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
        return l;
    }

    private class MemberRenderer implements ListCellRenderer<Member> {
        private final JPanel cell = new JPanel(new BorderLayout(8, 0));
        private final JLabel avatar = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel status = new JLabel();

        MemberRenderer() {
            cell.setBorder(Theme.padding(4, 6));
            name.setFont(Theme.bold(13));
            status.setFont(Theme.font(11));
            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);
            text.add(name);
            text.add(status);
            cell.add(avatar, BorderLayout.WEST);
            cell.add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Member> list, Member m, int index,
                                                      boolean selected, boolean focus) {
            Color dot = m.inGame() ? Theme.WARNING : Theme.SUCCESS;
            avatar.setIcon(new Avatar(m.nickname(), 34, dot));
            name.setText(m.nickname() + (m.owner() ? "  [방장]" : "") + (m.nickname().equals(myNickname) ? " (나)" : ""));
            name.setForeground(Theme.TEXT);
            status.setText(m.inGame() ? "게임 중" : "대기 중");
            status.setForeground(m.inGame() ? Theme.WARNING : Theme.SUCCESS);
            cell.setBackground(selected ? Theme.SURFACE_HIGH : Theme.SURFACE_LOW);
            cell.setOpaque(true);
            return cell;
        }
    }
}
