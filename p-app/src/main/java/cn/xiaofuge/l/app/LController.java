package cn.xiaofuge.l.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 美发沙龙管家 REST 接口。
 * 提供：服务价目 / 发型师列表 / 预约下单 / 预约查询 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class LController {

    private final LStore store;

    public LController(LStore store) {
        this.store = store;
    }

    /** 服务价目表 */
    @GetMapping("/services")
    public Map<String, Object> services() {
        return store.serviceList();
    }

    /** 发型师列表 */
    @GetMapping("/stylists")
    public Map<String, Object> stylists() {
        return store.stylistList();
    }

    /** 预约下单 */
    @PostMapping("/book")
    public Map<String, Object> book(@RequestBody Map<String, String> body) {
        return store.book(body.getOrDefault("customer", ""), body.getOrDefault("phone", ""),
                body.getOrDefault("service", ""), body.getOrDefault("stylistId", ""),
                body.getOrDefault("time", ""));
    }

    /** 预约查询 */
    @GetMapping("/order")
    public Map<String, Object> orderInfo(@RequestParam(required = false) String orderId) {
        return store.orderInfo(orderId == null ? "" : orderId);
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }
}
