package com.snw.client.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snw.client.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboundEventManager {

    // 自动化调度的实现
    private final StreamBridge streamBridge;

    private final ConcurrentMap<String, CompletableFuture<NextEventInfo>> pendingEvents = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    private long TypedelayMillis; // 存储延迟时间

    // 发布事件的方法
    public void publishEvent(Object event) {
        String topic = determineTopic(event); // 根据事件类型确定要发布的主题

        if (event instanceof OrderReceivedEvent) {
            // 处理商品到达事件
            messagingTemplate.convertAndSend("/topic/text", "开始出库的自动化调度");
            messagingTemplate.convertAndSend("/topic/text", "发起订单出库申请");
            messagingTemplate.convertAndSend("/topic/publishOrderReceived", event);

            // 等待分配结果并处理
            CompletableFuture<NextEventInfo> nextEventFuture = waitForNextEventInfo("OrderResult-out-0", event, topic);

            // 设置超时处理
            CompletableFuture<NextEventInfo> timeoutFuture = failAfterTimeout(10, TimeUnit.SECONDS);

            CompletableFuture.anyOf(nextEventFuture, timeoutFuture).thenAccept(result -> {
                if (result == timeoutFuture) {
                    // 处理超时情况
                    messagingTemplate.convertAndSend("/topic/text", "等待超时，操作失败");
                    log.info("Operation failed due to timeout.");
                } else {
                    NextEventInfo nextEventInfo = (NextEventInfo) result;
                    if (nextEventInfo.isSuccess()) {
                        long delayMillis = nextEventInfo.getTime() * 6 * 100; // 计算延迟时间（单位为毫秒）
                        TypedelayMillis = delayMillis;
                        messagingTemplate.convertAndSend("/topic/text", "需要等待" + delayMillis + "毫秒车才来");
                        log.info("Scheduling UnloadingStartedEvent with delay: {} milliseconds", delayMillis);
                        log.info("Scheduling UnloadingStartedEvent with delay: {} milliseconds", nextEventInfo.getVehicleId());

                        // 调度卸货开始事件
                        scheduleNextEvent(
                                new UnloadingStartedEvent(
                                        ((GoodsArrivedEvent) event).getShipmentId(),
                                        ((GoodsArrivedEvent) event).getWarehouseId(),
                                        nextEventInfo.getTimestamp(),
                                        nextEventInfo.getVehicleId()
                                ),
                                delayMillis
                        );
                    }else{
                        messagingTemplate.convertAndSend("/topic/text", "1操作失败");
                    }
                }
            });
        } else {
            // 处理其他类型的事件
            log.info("Unknown event type received.");
        }
    }

    private CompletableFuture<NextEventInfo> failAfterTimeout(long timeout, TimeUnit unit) {
        CompletableFuture<NextEventInfo> promise = new CompletableFuture<>();
        scheduler.schedule(() -> {
            promise.completeExceptionally(new TimeoutException("操作超时"));
        }, timeout, unit);
        //messagingTemplate.convertAndSend("/topic/text", "操作失败");
        return promise;
    }
    // 调度下一个事件的方法
    private void scheduleNextEvent(Object nextEvent, long delayMillis) {
        log.info("等待调度下一个事件");
        scheduler.schedule(() -> triggerNextEvent(nextEvent), delayMillis, TimeUnit.MILLISECONDS);
    }


    // 等待下一个事件信息的方法
    private CompletableFuture<NextEventInfo> waitForNextEventInfo(String topic, Object event, String a) {
        log.info("等待下一个事件的信息, 主题: {}", topic);
        CompletableFuture<NextEventInfo> result = new CompletableFuture<>();
        pendingEvents.put(topic, result);
        log.info("存储的未来事件信息, 主题: {}", topic);
        streamBridge.send(a, event);
        return result;
    }

    // 触发下一个事件的方法
    private void triggerNextEvent(Object nextEvent) {
        log.info("开始下一个事件的触发");
        publishEvent(nextEvent);
    }

    // 确定事件类型对应的主题的方法
    private String determineTopic(Object event) {
        if (event instanceof OrderReceivedEvent) {
            return "order-received";
        } else if (event instanceof UnloadingStartedEvent) {
            return "unloading-started-out-0";
        } else if (event instanceof UnloadingCompletedEvent) {
            return "unloading-completed-out-0";
        } else {
            return "unknown-topic";
        }
    }

    // 处理 OrderPostResult 类型事件的方法
    public void handleOrderPostResult(OrderPostResult event) {
        log.info("自动化调度获得 handleOrderPostResult event: {}", event);
        processSuccessResponse2("OrderResult-out-0", event.getStatus().equals("success"),event.getTime(), event.getLiftId());
    }

    // 处理 AssignmentResultEvent 类型事件的方法
    public void handleAssignmentResult(AssignmentResultEvent event) {
        log.info("自动化调度获得 AssignmentResult event: {}", event);
        processSuccessResponse2("assignment-result-out-0", event.getStatus().equals("success"), event.getTravelTime(), event.getVehicleId());
    }

    // 处理成功响应的方法
    private void processSuccessResponse(String topic, boolean success) {
        log.info("处理成功响应, 主题: {}", topic);
        CompletableFuture<NextEventInfo> future = pendingEvents.remove(topic);
        if (future != null) {
            log.info("完成未来事件信息, 主题: {}", topic);
            future.complete(new NextEventInfo(success));
        } else {
            log.warn("没有找到未来事件的信息, 主题: {}", topic);
        }
    }

    // 处理成功响应的方法（带时间和车辆ID）
    private void processSuccessResponse2(String topic, boolean success, int time, String vehicleId) {
        log.info("处理成功响应, 主题: {}", topic);
        CompletableFuture<NextEventInfo> future = pendingEvents.remove(topic);
        if (future != null) {
            log.info("完成未来事件信息, 主题: {}", topic);
            log.info("是否成功: {}", success);
            future.complete(new NextEventInfo(success, time, vehicleId));
        } else {
            log.warn("没有找到未来事件的信息, 主题: {}", topic);
        }
    }

    // 表示下一事件信息的内部类
    private static class NextEventInfo {
        private final boolean success; // 表示事件是否成功
        private final String timestamp; // 事件的时间戳
        private final int time; // 事件的时间
        private final String vehicleId; // 车辆ID

        public NextEventInfo(boolean success) {
            this(success, System.currentTimeMillis(), null, 5);
        }

        public NextEventInfo(boolean success, int time, String vehicleId) {
            this(success, System.currentTimeMillis(), vehicleId, time);
        }

        public NextEventInfo(boolean success, long timestamp, String vehicleId, int time) {
            this.success = success;
            this.timestamp = java.lang.String.valueOf(timestamp);
            this.vehicleId = vehicleId;
            this.time = time;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public int getTime() {
            return time;
        }

        public String getVehicleId() {
            return vehicleId;
        }
    }
}