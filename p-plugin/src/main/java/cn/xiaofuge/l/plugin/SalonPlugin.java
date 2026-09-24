package cn.xiaofuge.l.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 美发沙龙管家插件：把 hair-salon REST API 注册为 DSH Agent 工具 */
public class SalonPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "salon-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public SalonPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new ServiceListTool(),
                new StylistListTool(),
                new BookTool(),
                new OrderInfoTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("salon-capabilities", 20, """
                ## AI 美发沙龙管家（连锁美发运营 · 2026-09-25）
                - 查价目 → service_list（5 个项目价格与时长：精剪造型68/总监剪裁128/烫发368/染发298/头皮护理158；
                  烫染同做 8 折）
                - 查发型师 → stylist_list（3 位发型师职级/擅长/评分/在约单量）
                - 预约下单 → book（customer/phone/service/time 必填，stylistId 可选默认总监 S01；
                  必须先复述项目、价格、发型师、到店时间请顾客确认后才能调用；成功报单号与到店时间）
                - 预约查询 → order_info（orderId：H1001 格式；项目/发型师/时间/金额/状态）
                - 问运营 → stats（总单量/待到店/已完成/营收/分项目分发型师分布/营销建议）
                - 回答要求：
                  1) 预约前必须复述要素（项目/价格/发型师/时间）请顾客确认
                  2) 预约结果必报单号与到店时间
                  3) 烫染前提醒顾客做过敏测试；染后 48 小时内不洗头的护色提醒
                  4) 价格与时长只转述工具返回，禁止编造折扣
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: salon tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("SALON_APP_BASE_URL", "http://127.0.0.1:18110")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class ServiceListTool extends AbstractTool {
        @Override public String name() { return "service_list"; }
        @Override public String description() {
            return "美发价目表：5 个项目的价格与时长（精剪/总监剪/烫发/染发/头皮护理），含优惠规则。"
                    + "报价、预约前必查。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/services", args));
        }
    }

    private class StylistListTool extends AbstractTool {
        @Override public String name() { return "stylist_list"; }
        @Override public String description() {
            return "发型师列表：姓名/职级/擅长/评分/在约单量。顾客挑发型师、问谁剪得好时调用。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stylists", args));
        }
    }

    private class BookTool extends AbstractTool {
        @Override public String name() { return "book"; }
        @Override public String description() {
            return "预约下单：customer（预约人）/phone（联系电话）/service（项目）/time（到店时间）必填，"
                    + "stylistId（发型师 S01-S03）可选默认总监。必须先复述项目、价格、发型师、时间经顾客确认后才能调用。"
                    + "成功返回单号与到店时间。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customer", stringSchema("预约人姓名"))
                    .prop("phone", stringSchema("联系电话"))
                    .prop("service", stringSchema("项目：精剪造型 / 总监剪裁 / 烫发 / 染发 / 头皮护理"))
                    .prop("stylistId", stringSchema("发型师编号 S01-S03，可选，默认 S01 总监"))
                    .prop("time", stringSchema("期望到店时间，如：周四 15:00"))
                    .required("customer", "phone", "service", "time")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"customer\":\"" + json(str(args, "customer"))
                    + "\",\"phone\":\"" + json(str(args, "phone"))
                    + "\",\"service\":\"" + json(str(args, "service"))
                    + "\",\"stylistId\":\"" + json(str(args, "stylistId"))
                    + "\",\"time\":\"" + json(str(args, "time")) + "\"}";
            return ok(post("/api/book", body, args));
        }
    }

    private class OrderInfoTool extends AbstractTool {
        @Override public String name() { return "order_info"; }
        @Override public String description() {
            return "预约查询：orderId 必填（H1001 格式）。返回项目/发型师/时间/金额/状态（已预约、进行中、已完成）。"
                    + "何时必须调用：顾客问预约、问排期。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("orderId", stringSchema("预约单号，如 H1001"))
                    .required("orderId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/order?orderId=" + java.net.URLEncoder.encode(str(args, "orderId"), StandardCharsets.UTF_8), args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：总单量/待到店/已完成/营收与预计营收/分项目分发型师分布/营销建议。"
                    + "何时必须调用：问今天运营、问单量与营收。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
