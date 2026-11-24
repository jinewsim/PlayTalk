package playtalk.server.account;

import playtalk.common.Protocol;
import playtalk.server.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 회원 계정 (닉네임 = 아이디). 서버를 다시 켜도 유지되도록 CSV 파일에 저장한다.
 *
 * <p>비밀번호는 그대로 저장하지 않고, 계정마다 다른 무작위 salt를 붙여 SHA-256으로 해시한 값만 저장한다.
 * 파일이 유출되어도 원래 비밀번호를 바로 알 수 없다.
 *
 * <pre>
 * data/accounts.csv
 * nickname,salt,hash
 * Alex,q8r2...==,Zk1x...=
 * </pre>
 */
public class AccountStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path file;
    /** 닉네임 → [salt, hash] */
    private final Map<String, String[]> accounts = new LinkedHashMap<>();

    public AccountStore(Path file) {
        this.file = file;
        load();
    }

    /** @return 실패 사유 (성공하면 null) */
    public synchronized String register(String nickname, String password) {
        String error = Protocol.validateNickname(nickname);
        if (error == null) error = Protocol.validatePassword(password);
        if (error != null) return error;
        if (accounts.containsKey(nickname)) return "이미 가입된 닉네임입니다. 다른 닉네임을 입력하세요.";

        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltText = Base64.getEncoder().encodeToString(salt);
        accounts.put(nickname, new String[]{saltText, hash(saltText, password)});
        save();
        Log.info("회원가입: %s", nickname);
        return null;
    }

    /** 닉네임과 비밀번호가 맞는지 확인 */
    public synchronized boolean check(String nickname, String password) {
        String[] account = accounts.get(nickname);
        if (account == null || password == null) return false;
        // 길이가 같은 두 값을 끝까지 비교 (응답 시간으로 비밀번호를 추측하지 못하게)
        return MessageDigest.isEqual(
                account[1].getBytes(StandardCharsets.US_ASCII),
                hash(account[0], password).getBytes(StandardCharsets.US_ASCII));
    }

    /** 회원 탈퇴: 계정을 지운다. @return 지운 계정이 있었으면 true */
    public synchronized boolean delete(String nickname) {
        if (accounts.remove(nickname) == null) return false;
        save();
        Log.info("회원 탈퇴: %s", nickname);
        return true;
    }

    public synchronized boolean exists(String nickname) {
        return nickname != null && accounts.containsKey(nickname);
    }

    private static String hash(String salt, String password) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(salt.getBytes(StandardCharsets.US_ASCII));
            sha.update(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // 모든 JDK에 SHA-256이 있으므로 일어나지 않음
        }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file, Protocol.CHARSET)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",");
                if (f.length != 3 || f[0].equals("nickname")) continue;
                accounts.put(f[0], new String[]{f[1], f[2]});
            }
            Log.info("계정 파일 불러옴: %s (%d명)", file, accounts.size());
        } catch (IOException e) {
            Log.error("계정 파일을 읽지 못했습니다: %s", e);
        }
    }

    private void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, Protocol.CHARSET)) {
                w.write("nickname,salt,hash\n");
                for (var e : accounts.entrySet()) {
                    w.write(e.getKey() + "," + e.getValue()[0] + "," + e.getValue()[1] + "\n");
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.error("계정 저장 실패: %s", e);
        }
    }
}
