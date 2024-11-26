package cn.ustc.worker;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.ustc.constant.Constant;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class Read {
    private static final Logger log = LoggerFactory.getLogger(Read.class);
    private JSONObject wxReaderData;

    private JSONObject wxReaderHeader;

    private JSONObject wxReaderCookie;

    private Integer readNum = 2;

    private static Integer SLEEP_INTERVAL = 30;

    private static String KEY = "3c5c8717f3daf09iop3423zafeqoi";

    private static Map<String, String> COOKIE_DATA = new HashMap<>();

    static {
        COOKIE_DATA.put("rq", "%2Fweb%2Fbook%2Fread");
    }

    public Read(JSONObject wxReaderData, JSONObject wxReaderHeader, JSONObject wxReaderCookie, Integer readNum) {
        this.wxReaderData = wxReaderData;
        this.wxReaderHeader = wxReaderHeader;
        this.wxReaderCookie = wxReaderCookie;
        this.readNum = readNum;
    }

    public Read(JSONObject wxReaderData, JSONObject wxReaderHeader, JSONObject wxReaderCookie) {
        this.wxReaderData = wxReaderData;
        this.wxReaderHeader = wxReaderHeader;
        this.wxReaderCookie = wxReaderCookie;
    }

    private void initEnv() {
        Map<String, String> systemEnvMap = System.getenv();
        this.wxReaderCookie = JSON.parseObject(systemEnvMap.get(Constant.WX_READ_COOKIES));
        this.wxReaderHeader = JSON.parseObject(systemEnvMap.get(Constant.WX_READ_HEADERS));
        this.wxReaderData = JSON.parseObject(systemEnvMap.get(Constant.WX_READ_DATA));
        if (systemEnvMap.containsKey(Constant.READ_NUM)) {
            this.readNum = Integer.parseInt(systemEnvMap.get(Constant.READ_NUM));
        }
    }

    private String encodeData(JSONObject wxReaderData) {
        TreeMap<String, Object> sortedData = new TreeMap<>(wxReaderData);
        return sortedData.entrySet().stream()
                .map(entry -> {
                    try {
                        return URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString()) +
                                "=" +
                                URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8.toString());
                    } catch (Exception e) {
                        throw new RuntimeException("Error encoding key or value", e);
                    }
                })
                .collect(Collectors.joining("&"));
    }

    private String calHash(String inputStr) {
        long _7032f5 = 0x15051505L;
        long _cc1055 = _7032f5;
        int length = inputStr.length();
        int _19094e = length - 1;

        while (_19094e > 0) {
            _7032f5 = 0x7fffffffL & (_7032f5 ^ (long) inputStr.charAt(_19094e) << (length - _19094e) % 30);
            _cc1055 = 0x7fffffffL & (_cc1055 ^ (long) inputStr.charAt(_19094e - 1) << _19094e % 30);
            _19094e -= 2;
        }

        long hashValue = _7032f5 + _cc1055;
        return Long.toHexString(hashValue).toLowerCase();
    }

    private String getWrSkey() {
        try {
            HttpRequest request = HttpRequest.post(Constant.RENEW_URL)
                    .headerMap(this.jsonToMap(wxReaderHeader), true)
                    .form(COOKIE_DATA.toString())
                    .cookie(wxReaderCookie.toString());

            HttpResponse response = request.execute();
            String setCookieHeader = response.header("Set-Cookie");
            if (StrUtil.isNotBlank(setCookieHeader)) {
                for (String cookie : setCookieHeader.split(";")) {
                    if (cookie.contains("wr_skey")) {
                        return StrUtil.subAfter(cookie, "=", true).substring(0, 8);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Renew the Cookies Error", e);
        }
    }

    private Map<String, String> jsonToMap(JSONObject jsonObject) {
        Map<String, String> map = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);
            if (value != null) {
                map.put(key, value.toString());
            }
        }
        return map;
    }

    private Boolean readBook(Integer readTime, boolean retryFlag) {
        wxReaderData.put("ct", Instant.now().getEpochSecond());
        wxReaderData.put("ts", Instant.now().toEpochMilli());
        wxReaderData.put("rn", RandomUtil.randomInt(0, 1000));
        wxReaderData.put("sg", this.calSha256(wxReaderData.getString("ts") + wxReaderData.getString("rn") + KEY));
        wxReaderData.put("s", calHash(encodeData(wxReaderData)));
        try {
            HttpRequest request = HttpRequest.post(Constant.READ_URL)
                    .headerMap(this.jsonToMap(wxReaderHeader), true)
                    .form(COOKIE_DATA.toString())
                    .cookie(wxReaderCookie.toString());
            HttpResponse response = request.execute();
            JSONObject resData = JSON.parseObject(response.body());
            if (resData.containsKey("succ")) {
                return true;
            } else {
                if (retryFlag) {
                    String newWrSkey = this.getWrSkey();
                    if (newWrSkey != null) {
                        wxReaderCookie.put("wr_skey", newWrSkey);
                        log.info("Refresh the token success, the new token：{}, restart reading...", newWrSkey);
                        return readBook(readTime, false);
                    } else {
                        throw new RuntimeException("Cannot Get the New wr_skey");
                    }
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("An unknown exception occurs during reading.", e);
        } finally {
            wxReaderData.remove("s");
        }
    }

    private String calSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error calculating SHA-256", e);
        }
    }
}
