package org.walkmod.junit4git.core;


import com.ea.agentloader.AgentLoader;
import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class TestsReportClient {

    private final OkHttpClient client;

    private static Boolean loaded = false;

    private static Gson gson = new Gson();

    private static final String SERVER_TEST_REPORT_URL = "http://localhost:9000";

    public TestsReportClient() {
       this(new OkHttpClient(), !loaded);
    }

    public TestsReportClient(OkHttpClient client, boolean startupServer) {
        this.client = client;
        if (startupServer) {
            startUpAgentServer();
        }
    }

    protected void startUpAgentServer() {
        AgentLoader.loadAgentClass(TestsReportServer.class.getName(), "");
        loaded = true;
    }

    public void sendRequestToClassLoggerAgent(String className, String methodName, String event) {
        try {
            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                    gson.toJson(new JUnitEvent(event, className, methodName)));

            Request request = new Request.Builder()
                    .url(SERVER_TEST_REPORT_URL)
                    .post(body)
                    .build();

            client.newCall(request).execute();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
