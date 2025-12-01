package top.srcrs;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.srcrs.domain.Cookie;
import top.srcrs.util.Encryption;
import top.srcrs.util.Request;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Run {
    private static final Logger LOGGER = LoggerFactory.getLogger(Run.class);
    String LIKE_URL = "https://tieba.baidu.com/mo/q/newmoindex";
    String TBS_URL = "http://tieba.baidu.com/dc/common/tbs";
    String SIGN_URL = "http://c.tieba.baidu.com/c/c/forum/sign";
    private List<String> follow = new ArrayList<>();
    private static List<String> success = new ArrayList<>();
    private static HashSet<String> failed = new HashSet<String>();
    private static List<String> invalid = new ArrayList<>();
    private String tbs = "";
    private static Integer followNum = 201;

    public static void main(String[] args) {
        Cookie cookie = Cookie.getInstance();
        if (args.length == 0) {
            LOGGER.warn("请在Secrets中添加BDUSS");
        }
        cookie.setBDUSS(args[0]);
        Run run = new Run();
        run.getTbs();
        run.getFollow();
        run.runSign();
        LOGGER.info("共 {} 个贴吧 - 成功: {} - 失败: {} - {} ", followNum, success.size(), followNum - success.size(), failed);
        LOGGER.info("失效 {} 个贴吧: {} ", invalid.size(), invalid);
        if (args.length == 2) {
            run.send(args[1]);
        }
    }

    public void getTbs() {
        try {
            JSONObject jsonObject = Request.get(TBS_URL);
            if ("1".equals(jsonObject.getString("is_login"))) {
                LOGGER.info("TBS获取成功");
                tbs = jsonObject.getString("tbs");
            } else {
                LOGGER.warn("TBS获取失败 -- " + jsonObject);
            }
        } catch (Exception e) {
            LOGGER.error("TBS获取部分出现错误 -- " + e);
        }
    }

    public void getFollow() {
        try {
            JSONObject jsonObject = Request.get(LIKE_URL);
            LOGGER.info("贴吧列表获取成功");
            JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("like_forum");
            followNum = jsonArray.size();
            for (Object array : jsonArray) {
                String tiebaName = ((JSONObject) array).getString("forum_name");
                if ("0".equals(((JSONObject) array).getString("is_sign"))) {
                    follow.add(tiebaName.replace("+", "%2B"));
                    if (Request.isTiebaNotExist(tiebaName)) {
                        follow.remove(tiebaName);
                        invalid.add(tiebaName);
                        failed.add(tiebaName);
                    }
                } else {
                    success.add(tiebaName);
                }
            }
        } catch (Exception e) {
            LOGGER.error("贴吧列表获取部分出现错误 -- " + e);
        }
    }

    public void runSign() {
        Integer flag = 5;
        try {
            while (success.size() < followNum && flag > 0) {
                LOGGER.info("-----第 {} 轮签到开始-----", 5 - flag + 1);
                LOGGER.info("剩余 {} 个吧需要签到", followNum - success.size());
                Iterator<String> iterator = follow.iterator();
                while (iterator.hasNext()) {
                    String s = iterator.next();
                    String rotation = s.replace("%2B", "+");
                    String body = "kw=" + s + "&tbs=" + tbs + "&sign="
                            + Encryption.enCodeMd5("kw=" + rotation + "tbs=" + tbs + "tiebaclient!!!");
                    JSONObject post = new JSONObject();
                    post = Request.post(SIGN_URL, body);
                    int randomTime = new Random().nextInt(200) + 300;
                    LOGGER.info("等待 {} 毫秒", randomTime);
                    TimeUnit.MILLISECONDS.sleep(randomTime);
                    if ("0".equals(post.getString("error_code"))) {
                        iterator.remove();
                        success.add(rotation);
                        failed.remove(rotation);
                        LOGGER.info(rotation + ": " + "签到成功");
                    } else {
                        failed.add(rotation);
                        LOGGER.warn(rotation + ": " + "签到失败");
                    }
                }
                if (success.size() != followNum - invalid.size()) {
                    Thread.sleep(1000 * 60 * 5);
                    getTbs();
                }
                flag--;
            }
        } catch (Exception e) {
            LOGGER.error("签到部分出现错误 -- " + e);
        }
    }

    public void send(String sckey) {
        String body = "总共关注" + followNum + "个吧\n";
        body += "成功签到" + success.size() + "个吧\n" + "签到失败" + String.format("%02d", (followNum - success.size())) + "个吧";
        try {
            String token = sckey;
            String title = URLEncoder.encode("Tieba", "UTF-8");
            String content = URLEncoder.encode(body, "UTF-8");
            String scheme = URLEncoder.encode("com.baidu.tieba://", "UTF-8");
            String icon = "https://raw.githubusercontent.com/NOSR2006/Tieba/refs/heads/master/icon/Tieba.png";
            String urlx = "https://api.day.app/" + token + "/" + title + "/" + content + "?icon=" + icon
                    + "&url=" + scheme;
            URL url = new URL(urlx);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            System.out.println("Response: " + response.toString());
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}