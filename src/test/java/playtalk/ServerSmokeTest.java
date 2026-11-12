package playtalk;

import playtalk.common.*;
import playtalk.server.PlayTalkServer;
import java.io.*; import java.net.*; import java.nio.file.*; import java.util.*; import java.util.concurrent.*;

/**
 * 서버 통합 테스트 (JUnit 없이 main으로 실행).
 * 가짜 클라이언트 5명을 소켓으로 접속시켜 로그인, 채팅방, 채팅, 초대, 틱택토, 가위바위보,
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
    a.send(Message.of(MessageType.LOGIN).put("nickname","Alex")); check(a.await(MessageType.LOGIN_OK)!=null,"login Alex");
    b.send(Message.of(MessageType.LOGIN).put("nickname","Alex")); Message f=b.await(MessageType.LOGIN_FAIL); check(f.get("reason").contains("이미"),"duplicate nickname rejected");
    b.send(Message.of(MessageType.LOGIN).put("nickname","민수")); b.await(MessageType.LOGIN_OK);
    c.send(Message.of(MessageType.LOGIN).put("nickname","지은")); c.await(MessageType.LOGIN_OK);
    d.send(Message.of(MessageType.LOGIN).put("nickname","Dana")); d.await(MessageType.LOGIN_OK);
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
    C e=new C("E",19090); e.send(Message.of(MessageType.LOGIN).put("nickname","지은")); Thread.sleep(300); Message el=e.await(MessageType.LOGIN_OK); check(el!=null,"nickname reusable after disconnect");
    // owner transfer
    a.send(Message.of(MessageType.ROOM_LEAVE)); Message mm=b.await(MessageType.ROOM_MEMBERS, m->m.get("owner").equals("민수")); check(mm!=null,"owner transferred to 민수");
    // forfeit
    b.send(Message.of(MessageType.ROOM_LEAVE)); b.await(MessageType.ROOM_LEFT);
    System.out.println(fails==0?"ALL PASSED":("FAILURES: "+fails));
    srv.stop(); System.exit(fails==0?0:1);
  }
}
