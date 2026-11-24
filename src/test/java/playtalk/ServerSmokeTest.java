package playtalk;

import playtalk.common.*;
import playtalk.server.PlayTalkServer;
import java.io.*; import java.net.*; import java.nio.file.*; import java.util.*; import java.util.concurrent.*;

/**
 * 서버 통합 테스트 (JUnit 없이 main으로 실행).
 * 가짜 클라이언트 5명을 소켓으로 접속시켜 회원가입, 로그인, 친구, 채팅방, 채팅, 초대, 틱택토, 가위바위보,
 * 시간 맞추기(다인용), 연결 끊김, 방장 위임까지 실제 프로토콜로 검사한다.
 *
 * <pre>
 *   javac -encoding UTF-8 -d out/test -cp out/classes src/test/java/playtalk/ServerSmokeTest.java
 *   java -cp out/classes:out/test playtalk.ServerSmokeTest
 * </pre>
 * 서버 코드를 고친 뒤 push 전에 한 번씩 돌려 보세요. (약 15초 소요)
 */
public class ServerSmokeTest {
  static int fails=0;
  static void check(boolean c, String what){ System.out.println((c?"PASS ":"FAIL ")+what); if(!c) fails++; }
  static class C {
    String name; Socket s; BufferedReader in; PrintWriter out; BlockingQueue<Message> q=new LinkedBlockingQueue<>(); List<Message> seen=new ArrayList<>();
    C(String n,int port) throws Exception { name=n; s=new Socket("127.0.0.1",port); in=new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8")); out=new PrintWriter(new OutputStreamWriter(s.getOutputStream(),"UTF-8"),true);
      Thread t=new Thread(()->{try{String l;while((l=in.readLine())!=null)q.add(Message.decode(l));}catch(Exception e){}}); t.setDaemon(true); t.start(); }
    void send(Message m){ out.println(m.encode()); }
    /** 회원가입 후 로그인 */
    void join(String nick) throws Exception { send(Message.of(MessageType.REGISTER).put("nickname",nick).put("password","pw"+nick)); await(MessageType.REGISTER_OK); login(nick); }
    void login(String nick) throws Exception { send(Message.of(MessageType.LOGIN).put("nickname",nick).put("password","pw"+nick)); await(MessageType.LOGIN_OK); }
    Message await(MessageType t) throws Exception { return await(t, m->true); }
    Message await(MessageType t, java.util.function.Predicate<Message> p) throws Exception {
      for(Iterator<Message> it=seen.iterator(); it.hasNext();){ Message m=it.next(); if(m.getType()==t && p.test(m)){ it.remove(); return m; } }
      long end=System.currentTimeMillis()+20000;
      while(System.currentTimeMillis()<end){ Message m=q.poll(200,TimeUnit.MILLISECONDS); if(m==null) continue; if(m.getType()==t && p.test(m)) return m; seen.add(m); }
      throw new RuntimeException(name+" timeout waiting "+t);
    }
  }
  public static void main(String[] args) throws Exception {
    Path data=Files.createTempDirectory("pt");
    PlayTalkServer srv=new PlayTalkServer(19090,data);
    new Thread(()->{try{srv.start();}catch(Exception e){}}).start(); Thread.sleep(500);
    // escaping roundtrip
    Message rt=Message.decode(Message.of(MessageType.CHAT).put("text","a\tb\nc\\d=e").encode());
    check(rt.get("text").equals("a\tb\nc\\d=e"),"message escape roundtrip");
    check(Emoticons.split("hi :smile: yo :nope:").size()==3,"emoticon split");

    C a=new C("A",19090), b=new C("B",19090), c=new C("C",19090), d=new C("D",19090);
    // 회원가입 / 로그인
    b.send(Message.of(MessageType.LOGIN).put("nickname","Alex").put("password","pwAlex")); check(b.await(MessageType.LOGIN_FAIL).get("reason").contains("가입되지 않은"),"login before register rejected");
    b.send(Message.of(MessageType.ROOM_CREATE).put("title","x").put("max",2)); check(b.await(MessageType.ERROR).get("text").contains("로그인"),"request before login rejected");
    a.send(Message.of(MessageType.REGISTER).put("nickname","Alex").put("password","12")); check(a.await(MessageType.REGISTER_FAIL).get("reason").contains("4~20"),"short password rejected");
    a.join("Alex"); check(true,"register + login Alex");
    b.send(Message.of(MessageType.REGISTER).put("nickname","Alex").put("password","other1")); check(b.await(MessageType.REGISTER_FAIL).get("reason").contains("이미 가입"),"duplicate account rejected");
    b.send(Message.of(MessageType.LOGIN).put("nickname","Alex").put("password","wrong1")); check(b.await(MessageType.LOGIN_FAIL).get("reason").contains("비밀번호"),"wrong password rejected");
    b.send(Message.of(MessageType.LOGIN).put("nickname","Alex").put("password","pwAlex")); check(b.await(MessageType.LOGIN_FAIL).get("reason").contains("이미 접속"),"double login rejected");
    String acc=Files.readString(data.resolve("accounts.csv")); check(acc.contains("Alex,") && !acc.contains("pwAlex"),"account saved, password hashed");
    b.join("민수"); c.join("지은"); d.join("Dana");
    Message ul=a.await(MessageType.USER_LIST, m->m.getList("users").size()==4); check(ul!=null,"user list has 4");

    a.send(Message.of(MessageType.ROOM_CREATE).put("title","게임방").put("max",3));
    Message j=a.await(MessageType.ROOM_JOINED); int rid=j.getInt("roomId",-1); check(rid>0 && j.get("owner").equals("Alex"),"room created, owner Alex");
    b.send(Message.of(MessageType.ROOM_JOIN).put("roomId",rid)); b.await(MessageType.ROOM_JOINED);
    c.send(Message.of(MessageType.ROOM_JOIN).put("roomId",rid)); c.await(MessageType.ROOM_JOINED);
    d.send(Message.of(MessageType.ROOM_JOIN).put("roomId",rid)); Message rf=d.await(MessageType.ROOM_FAIL); check(rf.get("reason").contains("가득"),"full room rejected");
    Message rl=d.await(MessageType.ROOM_LIST, m->m.getTable("rooms").size()==1 && m.getTable("rooms").get(0)[2].equals("3")); check(rl!=null,"room list shows 3/3");

    b.send(Message.of(MessageType.CHAT).put("text","안녕 :smile: 반가워"));
    Message ch=c.await(MessageType.CHAT); check(ch.get("from").equals("민수") && ch.get("text").equals("안녕 :smile: 반가워"),"chat broadcast within room");
    d.send(Message.of(MessageType.CHAT).put("text","x")); check(d.await(MessageType.ERROR)!=null,"chat without room -> error");

    // invite outside room
    a.send(Message.of(MessageType.GAME_INVITE).put("target","Dana").put("game","TIC_TAC_TOE")); check(a.await(MessageType.ERROR).get("text").contains("같은 채팅방"),"invite outside room rejected");
    // TTT
    a.send(Message.of(MessageType.GAME_INVITE).put("target","민수").put("game","TIC_TAC_TOE"));
    a.await(MessageType.GAME_INVITE_RESULT, m->m.get("result").equals("PENDING"));
    Message inv=b.await(MessageType.GAME_INVITE); check(inv.get("from").equals("Alex"),"invite received");
    c.send(Message.of(MessageType.GAME_INVITE).put("target","민수").put("game","ROCK_PAPER_SCISSORS")); check(c.await(MessageType.ERROR).get("text").contains("다른 초대"),"double invite blocked");
    b.send(Message.of(MessageType.GAME_INVITE_REPLY).put("inviteId",inv.get("inviteId")).put("accept",true));
    Message gs=a.await(MessageType.GAME_START); b.await(MessageType.GAME_START); String sid=gs.get("sessionId");
    check(gs.get("x").equals("Alex") && gs.get("turn").equals("Alex"),"ttt start, Alex is X first");
    Message mem=c.await(MessageType.ROOM_MEMBERS, m->m.getTable("members").stream().filter(r->r[1].equals("IN_GAME")).count()==2); check(mem!=null,"members show IN_GAME");
    b.send(Message.of(MessageType.GAME_ACTION).put("sessionId",sid).put("cell",4)); check(b.await(MessageType.ERROR).get("text").contains("차례"),"out of turn rejected");
    int[] seq={0,3,1,4,2}; C[] who={a,b,a,b,a};
    for(int i=0;i<seq.length;i++){ who[i].send(Message.of(MessageType.GAME_ACTION).put("sessionId",sid).put("cell",seq[i])); final int cell=seq[i]; a.await(MessageType.GAME_STATE, m->m.getInt("last",-1)==cell); }
    Message end=b.await(MessageType.GAME_END); check(end.getTable("results").stream().anyMatch(r->r[0].equals("Alex")&&r[1].equals("WIN")),"ttt Alex wins top row");
    a.send(Message.of(MessageType.STATS_REQUEST)); Message st=a.await(MessageType.STATS); check(st.getInt("win",0)==1,"stats recorded win");
    check(Files.readString(data.resolve("stats.csv")).contains("Alex,TIC_TAC_TOE,1,0,0"),"stats saved to file");

    // RPS decline then accept + one times out
    a.send(Message.of(MessageType.GAME_INVITE).put("target","지은").put("game","ROCK_PAPER_SCISSORS"));
    Message inv2=c.await(MessageType.GAME_INVITE); c.send(Message.of(MessageType.GAME_INVITE_REPLY).put("inviteId",inv2.get("inviteId")).put("accept",false));
    check(a.await(MessageType.GAME_INVITE_RESULT, m->m.get("result").equals("DECLINED"))!=null,"invite declined");
    a.send(Message.of(MessageType.GAME_INVITE).put("target","지은").put("game","ROCK_PAPER_SCISSORS"));
    inv2=c.await(MessageType.GAME_INVITE); c.send(Message.of(MessageType.GAME_INVITE_REPLY).put("inviteId",inv2.get("inviteId")).put("accept",true));
    sid=a.await(MessageType.GAME_START).get("sessionId"); c.await(MessageType.GAME_START);
    a.send(Message.of(MessageType.GAME_ACTION).put("sessionId",sid).put("choice","ROCK"));
    c.await(MessageType.GAME_STATE, m->m.get("event").equals("CHOSEN"));
    c.send(Message.of(MessageType.GAME_ACTION).put("sessionId",sid).put("choice","SCISSORS"));
    end=c.await(MessageType.GAME_END); check(end.get("summary").contains("Alex 님 승리"),"rps rock beats scissors: "+end.get("summary"));

    // Timing multiplayer lobby with 3
    b.send(Message.of(MessageType.LOBBY_OPEN).put("game","TIMING"));
    Message ls=a.await(MessageType.LOBBY_STATE); String lid=ls.get("lobbyId"); check(ls.get("host").equals("민수"),"lobby opened by 민수");
    b.send(Message.of(MessageType.LOBBY_START).put("lobbyId",lid)); check(b.await(MessageType.ERROR).get("text").contains("최소"),"cannot start alone");
    a.send(Message.of(MessageType.LOBBY_JOIN).put("lobbyId",lid)); c.send(Message.of(MessageType.LOBBY_JOIN).put("lobbyId",lid));
    b.await(MessageType.LOBBY_STATE, m->m.getList("players").size()==3);
    b.send(Message.of(MessageType.LOBBY_START).put("lobbyId",lid));
    check(a.await(MessageType.LOBBY_CLOSED).get("reason").equals("STARTED"),"lobby closed on start");
    Message tgs=a.await(MessageType.GAME_START); sid=tgs.get("sessionId"); check(tgs.getList("players").size()==3,"timing has 3 players");
    a.await(MessageType.GAME_STATE, m->m.get("event").equals("RUNNING"));
    Thread.sleep(9800); a.send(Message.of(MessageType.GAME_ACTION).put("sessionId",sid).put("action","STOP"));
    Thread.sleep(800); b.send(Message.of(MessageType.GAME_ACTION).put("sessionId",sid).put("action","STOP"));
    c.s.close(); // C disconnects mid-game
    final String tsid=sid; Message rev=a.await(MessageType.GAME_STATE, m->m.get("sessionId").equals(tsid)&&m.get("event").equals("REVEAL"));
    System.out.println("   records: "+ rev.getTable("records").stream().map(Arrays::toString).toList());
    end=a.await(MessageType.GAME_END, m->m.get("sessionId").equals(tsid)); check(end.getTable("results").stream().anyMatch(r->r[0].equals("Alex")&&r[1].equals("WIN")),"timing closest wins: "+end.get("summary"));
    check(end.getTable("results").stream().anyMatch(r->r[0].equals("지은")&&r[1].equals("LOSS")),"disconnected player loses");
    a.seen.clear(); Message ul2=a.await(MessageType.USER_LIST, m->!m.getList("users").contains("지은")); check(ul2!=null,"disconnected user removed from list");
    // nickname reusable
    C e=new C("E",19090); Thread.sleep(300); e.login("지은"); check(true,"account can log in again after disconnect");
    // owner transfer
    a.send(Message.of(MessageType.ROOM_LEAVE)); Message mm=b.await(MessageType.ROOM_MEMBERS, m->m.get("owner").equals("민수")); check(mm!=null,"owner transferred to 민수");
    // forfeit
    b.send(Message.of(MessageType.ROOM_LEAVE)); b.await(MessageType.ROOM_LEFT);

    // 친구
    a.send(Message.of(MessageType.FRIEND_REQUEST).put("target","민수"));
    check(!a.await(MessageType.FRIEND_NOTICE).getBoolean("error"),"friend request sent");
    check(b.await(MessageType.FRIEND_REQUEST).get("from").equals("Alex"),"friend request received live");
    check(b.await(MessageType.FRIEND_LIST, m->m.getList("received").contains("Alex"))!=null,"request in receiver's list");
    a.send(Message.of(MessageType.FRIEND_REQUEST).put("target","민수")); check(a.await(MessageType.FRIEND_NOTICE, m->m.getBoolean("error")).get("text").contains("이미"),"duplicate friend request rejected");
    a.send(Message.of(MessageType.FRIEND_REQUEST).put("target","Alex")); check(a.await(MessageType.FRIEND_NOTICE, m->m.getBoolean("error")).get("text").contains("자기 자신"),"self friend rejected");
    a.send(Message.of(MessageType.FRIEND_REQUEST).put("target","없는사람")); check(a.await(MessageType.FRIEND_NOTICE, m->m.getBoolean("error")).get("text").contains("가입되지"),"unknown user rejected");
    b.send(Message.of(MessageType.FRIEND_REPLY).put("from","Alex").put("accept",true));
    check(a.await(MessageType.FRIEND_LIST, m->m.getTable("friends").stream().anyMatch(r->r[0].equals("민수")&&r[1].equals("true")))!=null,"accepted: 민수 online in Alex's list");
    check(a.await(MessageType.FRIEND_NOTICE, m->m.get("text").contains("수락"))!=null,"requester notified of accept");
    // 서로 요청하면 바로 친구
    d.send(Message.of(MessageType.FRIEND_REQUEST).put("target","Alex")); d.await(MessageType.FRIEND_NOTICE);
    a.send(Message.of(MessageType.FRIEND_REQUEST).put("target","Dana"));
    check(d.await(MessageType.FRIEND_LIST, m->m.getTable("friends").stream().anyMatch(r->r[0].equals("Alex")))!=null,"mutual requests become friends");
    // 보낸 요청 취소, 거절
    a.send(Message.of(MessageType.FRIEND_REQUEST).put("target","지은")); e.await(MessageType.FRIEND_LIST, m->m.getList("received").contains("Alex"));
    a.send(Message.of(MessageType.FRIEND_REMOVE).put("target","지은"));
    check(e.await(MessageType.FRIEND_LIST, m->m.getList("received").isEmpty())!=null,"sent request cancelled");
    e.send(Message.of(MessageType.FRIEND_REQUEST).put("target","민수")); b.await(MessageType.FRIEND_REQUEST);
    b.send(Message.of(MessageType.FRIEND_REPLY).put("from","지은").put("accept",false));
    check(e.await(MessageType.FRIEND_LIST, m->m.getList("sent").isEmpty())!=null,"declined request removed");
    String fr=Files.readString(data.resolve("friends.csv")); check(fr.contains("FRIEND,Alex,민수") && fr.contains("FRIEND,Alex,Dana"),"friends saved to file");
    // 접속 상태
    b.s.close();
    check(a.await(MessageType.FRIEND_LIST, m->m.getTable("friends").stream().anyMatch(r->r[0].equals("민수")&&r[1].equals("false")))!=null,"friend shown offline after disconnect");
    C b2=new C("B2",19090); b2.login("민수");
    check(b2.await(MessageType.FRIEND_LIST, m->m.getTable("friends").stream().anyMatch(r->r[0].equals("Alex")))!=null,"friend list sent on login");
    check(a.await(MessageType.FRIEND_LIST, m->m.getTable("friends").stream().anyMatch(r->r[0].equals("민수")&&r[1].equals("true")))!=null,"friend shown online after login");
    a.send(Message.of(MessageType.FRIEND_REMOVE).put("target","민수"));
    check(b2.await(MessageType.FRIEND_LIST, m->m.getTable("friends").isEmpty())!=null,"unfriend removes from both");
    // 서버를 다시 켜도 계정·친구 유지
    srv.stop(); Thread.sleep(300);
    PlayTalkServer srv2=new PlayTalkServer(19091,data); new Thread(()->{try{srv2.start();}catch(Exception ex){}}).start(); Thread.sleep(500);
    C a2=new C("A2",19091); a2.login("Alex");
    check(a2.await(MessageType.FRIEND_LIST, m->m.getTable("friends").stream().anyMatch(r->r[0].equals("Dana")&&r[1].equals("false")))!=null,"account and friends persist after restart");
    // 친구에게는 채팅방 없이 1:1 대결 신청
    C d2=new C("D2",19091); d2.login("Dana"); C e2=new C("E2",19091); e2.login("지은");
    a2.send(Message.of(MessageType.GAME_INVITE).put("target","지은").put("game","TIC_TAC_TOE"));
    check(a2.await(MessageType.ERROR).get("text").contains("친구"),"non-friend invite outside room rejected");
    a2.send(Message.of(MessageType.GAME_INVITE).put("target","Dana").put("game","TIC_TAC_TOE"));
    Message fi=d2.await(MessageType.GAME_INVITE); check(fi.get("from").equals("Alex"),"friend invite without room");
    d2.send(Message.of(MessageType.GAME_INVITE_REPLY).put("inviteId",fi.get("inviteId")).put("accept",true));
    Message fs=a2.await(MessageType.GAME_START); check(fs.getInt("roomId",0)==-1,"friend game starts outside room");
    a2.send(Message.of(MessageType.GAME_LEAVE).put("sessionId",fs.get("sessionId")));
    check(d2.await(MessageType.GAME_END)!=null,"friend game ends on leave");
    // 회원 탈퇴
    e2.send(Message.of(MessageType.FRIEND_REQUEST).put("target","Alex")); a2.await(MessageType.FRIEND_LIST, m->m.getList("received").contains("지은"));
    e2.send(Message.of(MessageType.ACCOUNT_DELETE).put("password","wrong1")); check(e2.await(MessageType.ACCOUNT_DELETE_FAIL).get("reason").contains("비밀번호"),"withdraw with wrong password rejected");
    e2.send(Message.of(MessageType.ACCOUNT_DELETE).put("password","pw지은")); check(e2.await(MessageType.ACCOUNT_DELETED)!=null,"account deleted");
    check(a2.await(MessageType.FRIEND_LIST, m->m.getList("received").isEmpty())!=null,"deleted user's friend request removed");
    check(!Files.readString(data.resolve("accounts.csv")).contains("지은,") && !Files.readString(data.resolve("stats.csv")).contains("지은,"),"account and stats removed from files");
    C e3=new C("E3",19091); e3.send(Message.of(MessageType.LOGIN).put("nickname","지은").put("password","pw지은")); check(e3.await(MessageType.LOGIN_FAIL).get("reason").contains("가입되지 않은"),"deleted account cannot log in");
    srv2.stop();
    System.out.println(fails==0?"ALL PASSED":("FAILURES: "+fails));
    srv.stop(); System.exit(fails==0?0:1);
  }
}
