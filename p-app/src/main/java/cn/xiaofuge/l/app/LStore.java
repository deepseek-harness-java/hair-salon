package cn.xiaofuge.l.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 美发沙龙数据中心：价目/发型师/预约/统计 */
@Component
public class LStore {

    /** 服务价目：项目/价格(元)/时长(分钟)/说明 */
    static final Map<String, Object[]> SERVICES = new LinkedHashMap<>();
    static {
        SERVICES.put("精剪造型", new Object[]{68.0, 60, "发型师主剪，含洗吹定型"});
        SERVICES.put("总监剪裁", new Object[]{128.0, 60, "总监一对一，含形象诊断"});
        SERVICES.put("烫发", new Object[]{368.0, 120, "数码烫/纹理烫，药水进口"});
        SERVICES.put("染发", new Object[]{298.0, 120, "植物染膏，含护色护理"});
        SERVICES.put("头皮护理", new Object[]{158.0, 45, "深层清洁+头皮检测"});
    }

    /** 发型师：编号/姓名/职级/擅长/评分 */
    static final Map<String, Object[]> STYLISTS = new LinkedHashMap<>();
    static {
        STYLISTS.put("S01", new Object[]{"阿凯", "总监", "精剪/烫发", 4.9});
        STYLISTS.put("S02", new Object[]{"小雅", "资深发型师", "染发/调色", 5.0});
        STYLISTS.put("S03", new Object[]{"大伟", "发型师", "男士造型/头皮护理", 4.8});
    }

    public static class Booking {
        public String id; public String customer; public String phone;
        public String service; public String stylist; public String time;
        public double total; public String status; // 已预约 / 进行中 / 已完成
    }

    public final List<Booking> bookings = new ArrayList<>();
    private int bookingSeq = 1001;

    public LStore() { seed(); }

    private void seed() {
        bookings.add(b("周先生", "13800004444", "精剪造型", "S01", "周三 14:00", "已预约"));
        bookings.add(b("钱女士", "13800005555", "染发", "S02", "周三 16:00", "进行中"));
        bookings.add(b("何女士", "13800006666", "头皮护理", "S03", "周二 11:00", "已完成"));
    }

    private Booking b(String customer, String phone, String service, String stylistId, String time, String status) {
        Booking x = new Booking(); x.id = "H" + bookingSeq++; x.customer = customer; x.phone = phone;
        x.service = service; x.stylist = String.valueOf(STYLISTS.get(stylistId)[0]); x.time = time;
        Object[] p = SERVICES.get(service);
        x.total = p != null ? (Double) p[0] : 0;
        x.status = status; return x;
    }

    /** 服务价目表 */
    public Map<String, Object> serviceList() {
        List<Map<String, Object>> list = new ArrayList<>();
        SERVICES.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("service", k); m.put("price", v[0]); m.put("minutes", v[1]); m.put("desc", v[2]); list.add(m); });
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("services", list);
        r.put("note", "烫发染发同做享 8 折，会员剪发 9 折");
        return r;
    }

    /** 发型师列表 */
    public Map<String, Object> stylistList() {
        List<Map<String, Object>> list = STYLISTS.entrySet().stream()
                .map(e -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", e.getKey()); m.put("name", e.getValue()[0]);
                    m.put("level", e.getValue()[1]); m.put("skill", e.getValue()[2]);
                    m.put("rating", e.getValue()[3]);
                    m.put("activeBookings", bookings.stream().filter(b -> b.stylist.equals(e.getValue()[0])
                            && !"已完成".equals(b.status)).count());
                    return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("stylists", list);
        return r;
    }

    /** 预约下单 */
    public synchronized Map<String, Object> book(String customer, String phone, String service, String stylistId, String time) {
        if (customer == null || customer.isBlank())
            return Map.of("ok", false, "msg", "请提供预约人姓名");
        Object[] p = SERVICES.get(service);
        if (p == null) return Map.of("ok", false, "msg", "项目 " + service + " 不在价目表，可选：" + String.join("/", SERVICES.keySet()));
        if (phone == null || phone.isBlank())
            return Map.of("ok", false, "msg", "请提供联系电话，方便到店提醒");
        if (time == null || time.isBlank())
            return Map.of("ok", false, "msg", "请提供期望到店时间（如：周四 15:00）");
        String stylistName;
        if (stylistId == null || stylistId.isBlank()) {
            stylistName = String.valueOf(STYLISTS.get("S01")[0]); // 默认总监
        } else {
            var sEntry = STYLISTS.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(stylistId)).findFirst().orElse(null);
            if (sEntry == null) return Map.of("ok", false, "msg", "发型师 " + stylistId + " 不存在，可选：" + String.join("/", STYLISTS.keySet()));
            stylistName = String.valueOf(sEntry.getValue()[0]);
        }
        Booking x = new Booking(); x.id = "H" + bookingSeq++; x.customer = customer; x.phone = phone;
        x.service = service; x.stylist = stylistName; x.time = time;
        x.total = (Double) p[0]; x.status = "已预约";
        bookings.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("bookingId", x.id); r.put("customer", customer);
        r.put("service", service); r.put("stylist", stylistName); r.put("time", time); r.put("total", x.total);
        r.put("minutes", p[1]);
        r.put("msg", "预约成功！单号 " + x.id + "，" + service + " ¥" + x.total + "，发型师 " + stylistName + "，" + time + " 到店，耗时约 " + p[1] + " 分钟");
        return r;
    }

    /** 预约查询 */
    public Map<String, Object> orderInfo(String orderId) {
        Booking x = bookings.stream().filter(b -> b.id.equalsIgnoreCase(orderId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "预约单 " + orderId + " 不存在，当前共 " + bookings.size() + " 单");
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("bookingId", x.id); r.put("customer", x.customer);
        r.put("service", x.service); r.put("stylist", x.stylist); r.put("time", x.time);
        r.put("total", x.total); r.put("status", x.status);
        if ("已完成".equals(x.status)) r.put("msg", "本次服务已完成，欢迎下次光临");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byService = new LinkedHashMap<String, Object>();
        for (String s : SERVICES.keySet()) {
            long n = bookings.stream().filter(b -> s.equals(b.service)).count();
            if (n > 0) byService.put(s, n + " 单");
        }
        Map<String, Object> byStylist = new LinkedHashMap<String, Object>();
        for (var e : STYLISTS.entrySet()) {
            long n = bookings.stream().filter(b -> b.stylist.equals(e.getValue()[0])).count();
            byStylist.put(String.valueOf(e.getValue()[0]), n + " 单");
        }
        long upcoming = bookings.stream().filter(b -> "已预约".equals(b.status) || "进行中".equals(b.status)).count();
        double revenue = bookings.stream().filter(b -> "已完成".equals(b.status)).mapToDouble(b -> b.total).sum();
        double expected = bookings.stream().filter(b -> !"已完成".equals(b.status)).mapToDouble(b -> b.total).sum();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("totalBookings", bookings.size());
        r.put("upcoming", upcoming);
        r.put("done", bookings.stream().filter(b -> "已完成".equals(b.status)).count());
        r.put("revenue", revenue);
        r.put("expectedRevenue", expected);
        r.put("byService", byService);
        r.put("byStylist", byStylist);
        r.put("advice", "烫染客单价高可推同做 8 折套餐；周末黄金档建议提前 2 天锁位；头皮护理可作剪发后加购项");
        return r;
    }
}
