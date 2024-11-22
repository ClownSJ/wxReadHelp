package cn.ustc.worker;

import cn.ustc.constant.Constant;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.Map;

public class Read {

    private JSONObject wxReaderData;

    private JSONObject wxReaderHeader;

    private JSONObject wxReaderCookie;

    private Integer readNum = 2;

    private static Integer SLEEP_INTERVAL = 30;

    public void readBook() {

    }

    private void init() {
        Map<String, String> systemEnvMap = System.getenv();
        this.wxReaderCookie = JSON.parseObject(systemEnvMap.get(Constant.WX_READ_COOKIES));
        this.wxReaderHeader = JSON.parseObject(systemEnvMap.get(Constant.WX_READ_HEADERS));
        this.wxReaderData = JSON.parseObject(systemEnvMap.get(Constant.WX_READ_DATA));
        if (systemEnvMap.containsKey(Constant.READ_NUM)) {
            this.readNum = Integer.parseInt(systemEnvMap.get(Constant.READ_NUM));
        }
    }
}
