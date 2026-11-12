package playtalk.server;

import playtalk.common.Protocol;

import java.nio.file.Path;

/**
 * 서버 실행 진입점.
 * <pre>
 *   java -cp target/classes playtalk.server.ServerMain [포트]
 * </pre>
 */
public class ServerMain {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.DEFAULT_PORT;
        PlayTalkServer server = new PlayTalkServer(port, Path.of("data"));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
