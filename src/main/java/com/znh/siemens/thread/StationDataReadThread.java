package com.znh.siemens.thread;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.util.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.xingshuangs.iot.protocol.s7.service.S7PLC;
import com.znh.siemens.controller.TighteningController;
import com.znh.siemens.model.*;
import com.znh.siemens.utils.ApiService;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public class StationDataReadThread extends Thread {
    private final S7PLC s7PLC;
    private final Station station;
    private final String timeUnit;
    private final int interval;
    public volatile boolean isRunning = true;

    // 使用AtomicBoolean以确保线程安全
    private final AtomicBoolean cleaned = new AtomicBoolean(false);

    public StationDataReadThread(S7PLC s7PLC, Station station, String timeUnit, int interval) {
        this.s7PLC = s7PLC;
        this.station = station;
        this.timeUnit = timeUnit;
        this.interval = interval;
        // 设置线程为守护线程，这样当主程序退出时，线程会自动终止
        setDaemon(true);
        // 设置线程名称，方便调试
        setName("Station-" + station.getName() + "-Thread");
    }

    // 标记是否已经调用过入站接口和出站接口，避免重复调用
    private boolean hasCalledInPass = false;
    private boolean hasCalledOutPass = false;
    private boolean hasBindSn = false;
    private boolean hasCheck = false;
    // 标记是否已经处理过普通项和SN项
    private boolean hasProcessedCommonItems = false;
    // 存储每个监听项的上一次状态，用于避免重复处理同一状态
    private Map<Integer, String> lastItemStatus = new HashMap<>();

    @Override
    public void run() {
        log.info("工位[{}]数据读取线程已启动", station.getName());

        try {
            String ldNum = "";
            TestDataModel testModel = new TestDataModel();
            CheckDataModel checkModel = new CheckDataModel();
            PassData passData = new PassData();
            testModel.setNodeCode(station.getProcess());
            checkModel.setNodeCode(station.getProcess());
            testModel.setStationCode(station.getName());
            checkModel.setStationCode(station.getName());
            passData.setNodeCode(station.getProcess());
            passData.setStationCode(station.getName());

            // 处理普通项和SN项 - 只在线程启动时执行一次
            if (!hasProcessedCommonItems) {
                processCommonItems(testModel, passData);
                hasProcessedCommonItems = true;
            }

            while (isRunning) {
                try {
                    // 如果PLC连接已断开，则停止线程
                    if (s7PLC == null || !s7PLC.checkConnected()) {
                        log.warn("plc disconnect!!  thread is interrupted!", station.getName());
                        break;
                    }

                    // 处理监听项 - 循环读取数据
                    for (int i = 0; i < station.getItems().size(); i++) {
                        // 如果线程被要求停止，则立即退出循环
                        if (!isRunning) {
                            break;
                        }

                        StationItem item = station.getItems().get(i);
                        // 处理心跳项
                        if ("心跳项".equals(item.getItemType())) {
                            String result = readItemValue(item);
                            final String finalResult = result;

                            // 使用Platform.runLater更新UI，但要检查线程是否仍在运行
                            if (isRunning) {
                                Platform.runLater(() -> {
                                    if (item.getResultLabel() != null) {
                                        item.getResultLabel().setText(finalResult);
                                    }
                                });
                            }

                            // 当心跳项值为1时，立即复位为0
                            if ("1".equals(result)) {
                                writeItemValue(item, "0");
                            }
                        }

                        if ("监听项".equals(item.getItemType())) {
                            String result = readItemValue(item);
                            final String finalResult = result;

                            // 使用Platform.runLater更新UI，但要检查线程是否仍在运行
                            if (isRunning) {
                                Platform.runLater(() -> {
                                    if (item.getResultLabel() != null) {
                                        item.getResultLabel().setText(finalResult);
                                    }
                                });
                            }

                            StationItem writeItem = findNextWriteItem(i);

                            // 获取该监听项的上一次状态，如果不存在则默认为空字符串
                            String lastStatus = lastItemStatus.getOrDefault(i, "");

                            // 只有当状态发生变化时才执行相应的操作
                            if (!result.equals(lastStatus)) {
                                // 更新该监听项的状态
                                lastItemStatus.put(i, result);

                                // 当监听项结果为1时，调用入站接口（只有第一个监听项可以有1状态值）
                                if ("1".equals(result) && !hasCalledInPass) {
                                    log.info("Monitor {}---------------------------------1-----------------------------------[start]", station.getName());
                                    processSnItems(testModel, passData, checkModel);
                                    handleInPassOperation(item, writeItem, passData);
                                    log.info("Monitor {}---------------------------------1-----------------------------------[end]", station.getName());
                                }
                                // 当监听项结果为2时，读取后续的读取项和函数项，上传检测数据但不调用出站接口
                                else if ("2".equals(result)) {
                                    log.info("Monitor {}-------------------------------------2-----------------------------------[start]", station.getName());
                                    TestDataModel ldTestModel = processLdItems();
                                    handleTestOperation(ldTestModel, i, writeItem, testModel, false);
                                    log.info("Monitor {}-------------------------------------2-----------------------------------[end]", station.getName());
                                }
                                // 当监听项结果为3时，读取后续的读取项和函数项，上传检测数据并调用出站接口（只有最后一个监听项有3状态值）
                                else if ("3".equals(result) && !hasCalledOutPass) {
                                    log.info("Monitor {}-------------------------------------3-----------------------------------[start]", station.getName());
                                    AtomicBoolean checkResult = new AtomicBoolean(true);
                                    List<StationItem> codeItems = station.getItems().stream().filter(ele -> ele.getItemType().equals("Code项")).collect(Collectors.toList());
                                    if (!codeItems.isEmpty()) {
                                        checkResult.set(false);
                                        for (StationItem codeItem : codeItems) {
                                            processCodeItem(codeItem, checkModel);
                                            if (!handleCheckOperation(writeItem, checkModel)) {
                                                checkResult.set(false);
                                                break;
                                            }
                                            checkResult.set(true);
                                        }
                                    }
                                    if (checkResult.get()) {
                                        TestDataModel ldTestModel = processLdItems();
                                        handleTestOperation(ldTestModel, i, writeItem, testModel, true);
                                    }
                                    log.info("Monitor {}-------------------------------------3-----------------------------------[end]", station.getName());
                                } else if ("4".equals(result) && !hasBindSn) {
                                    log.info("Monitor {}-------------------------------------4-------------------------------------[start]", station.getName());
                                    handleBindSnOperation(writeItem);
                                    log.info("Monitor {}-------------------------------------4-------------------------------------[end]", station.getName());
                                }
                                // 当监听项结果为0时，复位操作
                                else if ("0".equals(result)) {
                                    handleResetOperation(writeItem);
                                    // 如果监听项变为0，重置入站和出站标志，允许再次调用
                                    if (writeItem != null) {
                                        hasCalledInPass = false;
                                        hasCalledOutPass = false;
                                        hasBindSn = false;
                                        hasCheck = false;
                                    }
                                }
                            }
                        }
                    }

                    // 根据时间单位设置休眠时间
                    if ("s".equals(timeUnit)) {
                        TimeUnit.SECONDS.sleep(interval);
                    } else {
                        TimeUnit.MILLISECONDS.sleep(interval);
                    }
                } catch (InterruptedException e) {
                    log.warn("工位[{}]线程被中断，将终止运行", station.getName());
                    Thread.currentThread().interrupt(); // 重设中断状态
                    break;
                } catch (Exception e) {
                    log.error("工位[{}]线程处理数据时发生异常: {}", station.getName(), e.getMessage(), e);
                    // 发生异常后短暂休眠，避免CPU占用过高
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            // 确保线程结束时清理资源
            cleanup();
            log.info("工位[{}]线程已终止", station.getName());
        }
    }

    /**
     * 清理线程使用的资源
     */
    public void cleanup() {
        // 使用AtomicBoolean确保资源只被清理一次
        if (cleaned.compareAndSet(false, true)) {
            log.info("工位[{}]线程开始清理资源", station.getName());

            try {
                // 清理映射和缓存
                if (lastItemStatus != null) {
                    lastItemStatus.clear();
                    lastItemStatus = null;
                }

                // 清理其他可能占用内存的引用
                // 注意：不要清理s7PLC等共享资源的引用，因为它们不是由本线程创建的

                // 建议GC回收
                System.gc();

                log.info("工位[{}]线程资源清理完成", station.getName());
            } catch (Exception e) {
                log.error("工位[{}]线程清理资源时发生异常: {}", station.getName(), e.getMessage(), e);
            }
        }
    }

    private void processCodeItem(StationItem item, CheckDataModel checkDataModel) {
        log.info("{} station starts processing Code item data", station.getName());
        try {
            // 处理Code项
            if ("Code项".equals(item.getItemType())) {
                String result = readItemValue(item);
                log.info("{} station Code item reading result: {}", station.getName(), result);
                final String finalResult = result;
                Platform.runLater(() -> item.getResultLabel().setText(finalResult));
                checkDataModel.setAssemblyCode(finalResult);
            }

        } catch (Exception e) {
            log.error("{} station Code item processing exception: {}", station.getName(), e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private boolean handleCheckOperation(StationItem writeItem, CheckDataModel checkModel) {
        if (hasCheck) {
            log.info("has checked");
            return false;
        }
        String checkModelJson = JSONObject.toJSONString(checkModel);
        log.info("{} station starts processing check operation... data:{}", station.getName(), checkModelJson);

        boolean success = ApiService.sendCheckData(checkModelJson);
        // 如果找到了写入项，根据测试结果写入相应的值
        if (writeItem != null && !hasCheck) {
            String writeValue = success ? "1" : "2"; // 1表示OK，2表示NG
            log.info("{} station check operation write PLC result: {}", station.getName(), writeValue);
            writeItemValue(writeItem, writeValue);
            hasCheck = true;
            log.info("{} station check operation processing completed", station.getName());
        } else if (writeItem == null) {
            log.error("{} station check operation failed: No corresponding write item found", station.getName());
        }
        return success;
    }

    /**
     * 处理普通项和SN项 - 只执行一次
     */
    private void processCommonItems(TestDataModel testModel, PassData passData) {
        try {
            for (StationItem item : station.getItems()) {
                // 处理普通项
                if ("普通项".equals(item.getItemType())) {
                    String result = readItemValue(item);
                    final String finalResult = result;
                    Platform.runLater(() -> item.getResultLabel().setText(finalResult));
                }


            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processSnItems(TestDataModel testModel, PassData passData, CheckDataModel checkModel) {
        try {
            for (StationItem item : station.getItems()) {
                // 处理SN项
                if ("SN项".equals(item.getItemType())) {
                    String result = readItemValue(item);
                    log.info("Reading SN: " + result);
                    final String finalResult = result;
                    Platform.runLater(() -> item.getResultLabel().setText(finalResult));
                    testModel.setSn(finalResult);
                    testModel.setLineCode("CX03");
                    checkModel.setSn(finalResult);
                    checkModel.setLineCode("CX03");
                    ;
                    passData.setSn(finalResult);
                    passData.setLineCode("CX03");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TestDataModel processLdItems() {
        log.info("{} station starts processing screw number item data", station.getName());
        String ldNum = "";
        TestDataModel testModel = new TestDataModel();
        try {
            for (StationItem item : station.getItems()) {
                if ("螺钉号项".equals(item.getItemType())) {
                    log.info("{} station found screw number item: {}", station.getName(), item.getName());
                    ldNum = readItemValue(item);
                    log.info("{} station screw number item original reading value: {}", station.getName(), ldNum);

                    final String finalResult = ldNum;
                    Platform.runLater(() -> item.getResultLabel().setText(finalResult));
                    log.info("{} station screw number item final value: {}", station.getName(), finalResult);

                    // 获取拧紧控制器数据
                    log.info("{} station starts getting tightening controller data...", station.getName());
                    Map<String, Object> tighteningData = getTighteningData();
                    if (tighteningData != null) {
                        List<Map<String, Object>> results = (List<Map<String, Object>>) tighteningData.get("results");
                        if (results != null && !results.isEmpty()) {
                            Map<String, Object> firstResult = results.get(0);
                            List<Map<String, Object>> steps = (List<Map<String, Object>>) firstResult.get("steps");
                            if (steps != null && !steps.isEmpty()) {
                                Map<String, Object> firstStep = steps.get(0);
                                Map<String, Object> data = (Map<String, Object>) firstStep.get("data");
                                if (data != null) {
                                    // 提取 finalAngle 和 finalTorque
                                    Object angleValue = data.get("finalAngle");
                                    Object torqueValue = data.get("finalTorque");
                                    String angleValueStr = angleValue != null ? angleValue.toString() : null;
                                    String torqueValueStr = torqueValue != null ? torqueValue.toString() : null;
                                    // 记录获取到的数据
                                    final String angleStr = angleValueStr;
                                    final String torqueStr = torqueValueStr;
                                    final String unitStr = "Nm";

                                    log.info("{} station successfully got tightening controller data: {}", station.getName(), JSONObject.toJSONString(tighteningData));


                                    log.info("{} station tightening data parsing result - angle value: {}, torque value: {}, torque unit: {}",
                                            station.getName(), angleStr, torqueStr, unitStr);

                                    Platform.runLater(() -> {
                                        // 更新UI显示
                                        item.getResultLabel().setText(finalResult + " [angle:" + angleStr + ", torque:" + torqueStr + unitStr + "]");
                                    });

                                    // 添加测试项
                                    testModel.addTestItem(ldNum, "螺钉角度", angleStr, "", "");
                                    testModel.addTestItem(ldNum, "螺丝扭矩", torqueStr, unitStr, "");
                                    log.info("{} station has added tightening data to test model", station.getName());
                                }
                            }
                        }
                    } else {
                        log.warn("{} station failed to get tightening controller data", station.getName());
                    }
                }
            }
        } catch (Exception e) {
            log.error("{} station processing screw number item data exception: {}", station.getName(), e.getMessage(), e);
            e.printStackTrace();
        } finally {
            log.info("{} station screw number item processing completed, test item count: {}", station.getName(),
                    testModel.getTestData() != null ? testModel.getTestData().size() : 0);
            return testModel;
        }
    }

    /**
     * 获取拧紧控制器数据
     * 尝试从TighteningController获取数据，如果没有数据则等待一段时间再重试
     *
     * @return 拧紧控制器数据Map
     */
    private Map<String, Object> getTighteningData() {
        TighteningController tighteningController = TighteningController.getInstance(0);
        Map<String, Object> data = null;
        int maxRetries = 10; // 最大重试次数
        int retryCount = 0;

        try {
            while (retryCount < maxRetries) {
                // 获取接收到的数据

                String receivedString = tighteningController.getReceivedString();
                Map<String, Object> receivedData = tighteningController.getReceivedData();

                // 检查是否有数据
                if (receivedString != null && !receivedString.isEmpty()) {
                    // 如果receivedData已经是解析好的Map，直接使用
                    if (receivedData != null && !receivedData.isEmpty()) {
                        data = receivedData;
                        log.info("demeter data:" + receivedString);
                        break;
                    } else {
                        // 否则尝试解析receivedString
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            data = mapper.readValue(receivedString, Map.class);
                            break;
                        } catch (Exception e) {
                            log.info("parse demeter data error: " + e.getMessage());
                        }
                    }
                }

                // 如果没有数据，等待一段时间再重试
                retryCount++;
                log.info("retry, count: " + retryCount);
                TimeUnit.MILLISECONDS.sleep(500); // 等待500毫秒
            }

            if (retryCount >= maxRetries) {
                log.info("The timeout for obtaining the tightening data has occurred, and the maximum number of retries has been reached.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }


    /**
     * 查找后续最近的写入项
     */
    private StationItem findNextWriteItem(int currentIndex) {
        for (int j = currentIndex + 1; j < station.getItems().size(); j++) {
            StationItem nextItem = station.getItems().get(j);
            if ("写入项".equals(nextItem.getItemType())) {
                return nextItem;
            }
        }
        return null;
    }

    /**
     * 处理入站操作
     */
    private void handleInPassOperation(StationItem item, StationItem writeItem, PassData passData) {
        if (writeItem != null) {
            log.info("{} station starts processing inbound operation...", station.getName());
            // Set inbound type and call inbound interface
            passData.setPassType(1); // 1 means inbound
            passData.setPassTime(DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
            String jsonPassData = JSONObject.toJSONString(passData);

            log.info("{} station inbound interface request data: {}", station.getName(), jsonPassData);
            boolean success = sendPassData(jsonPassData);
            log.info("{} station inbound interface call result: {}", station.getName(), success ? "success" : "failure");

            // Write corresponding value based on interface return result (OK/NG)
            String writeValue = success ? "1" : "2"; // 1 means OK, 2 means NG
            log.info("{} station inbound operation write PLC result: {}", station.getName(), writeValue);
            writeItemValue(writeItem, writeValue);

            // Mark inbound interface as called to avoid duplicate calls
            hasCalledInPass = true;
            log.info("{} station inbound operation processing completed", station.getName());
        } else {
            log.error("{} station inbound operation failed: No corresponding write item found", station.getName());
            throw new RuntimeException("No corresponding write item found");
        }
    }

    /**
     * 处理检测操作
     *
     * @param callOutPass 是否需要调用出站接口
     */
    private void handleTestOperation(TestDataModel ldTestModel, int currentIndex, StationItem writeItem, TestDataModel testModel, boolean callOutPass) {
        log.info("{} station starts processing test operation, need to call outbound interface: {}", station.getName(), callOutPass);
        boolean allTestSuccess = true;
        boolean hasProcessedItems = true;

        // Find and process subsequent read items and function items
        for (int j = currentIndex + 1; j < station.getItems().size(); j++) {
            StationItem nextItem = station.getItems().get(j);

            // If encounter the next monitor item, stop processing
            if ("监听项".equals(nextItem.getItemType())) {
                break;
            }

            if ("读取项".equals(nextItem.getItemType())) {
                log.info("{} station starts processing read item: {}, time: {}", station.getName(), nextItem.getName(), DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));

                hasProcessedItems = true;
                String readResult = readItemValue(nextItem);
                log.info("{} station read item: {} reading result: {}", station.getName(), nextItem.getName(), readResult);
                final String finalReadResult = readResult;
                Platform.runLater(() -> nextItem.getResultLabel().setText(finalReadResult));

                testModel.setTestTime(DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
                if (ldTestModel.getTestData().isEmpty()) { //non-screw item
                    testModel.addTestItem("", nextItem.getName(), finalReadResult, "", "bool".equals(nextItem.getDataType()) && "false".equals(finalReadResult) ? "NG" : "OK");
                    log.info("{} station adding non-screw test item: {}, value: {}", station.getName(), nextItem.getName(), finalReadResult);
                } else {
                    //screw item
                    log.info("{} station adding screw test item data, total {} items", station.getName(), ldTestModel.getTestData().size());
                    for (TestDataModel.TestItem testItem : ldTestModel.getTestData()) {
                        testItem.setTestResult(StringUtils.isNotEmpty(finalReadResult) ? "OK" : "NG");
                        testModel.addTestItem(testItem);
                        log.info("{} station screw test item: {}, value: {}, result: {}", station.getName(), testItem.getTestItem(), testItem.getTestValue(), testItem.getTestResult());
                    }
                }

                // Upload test data to MES
                String testDataJson = JSONObject.toJSONString(testModel);
                log.info("{} station uploading test data to MES: {}", station.getName(), testDataJson);
                boolean success = ApiService.sendTestData(testDataJson);
                log.info("{} station test data upload result: {}", station.getName(), success ? "success" : "failure");

                allTestSuccess = allTestSuccess && success;
                log.info("{} station completed processing read item: {}, time: {}", station.getName(), nextItem.getName(), DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
                testModel.clearData();
            } else if ("函数项_x".equals(nextItem.getItemType()) || "函数项_y".equals(nextItem.getItemType())) {
                log.info("{} station starts processing function item: {}, time: {}", station.getName(), nextItem.getName(), DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
                hasProcessedItems = true;
                testModel.clearData();
                StationItem xItem, yItem;

                if ("函数项_x".equals(nextItem.getItemType())) {
                    xItem = nextItem;
                    yItem = station.getItems().get(j + 1);
                    log.info("{} station processing X-Y function item: X={}, Y={}", station.getName(), xItem.getName(), yItem.getName());
                    j++; // Skip the next item as it's already processed
                } else {
                    yItem = nextItem;
                    xItem = station.getItems().get(j + 1);
                    log.info("{} station processing Y-X function item: Y={}, X={}", station.getName(), yItem.getName(), xItem.getName());
                    j++; // Skip the next item as it's already processed
                }

                String xResult = readItemValue(xItem);
                String yResult = readItemValue(yItem);
                log.info("{} station function item reading result: X={}, Y={}", station.getName(), xResult, yResult);

                Platform.runLater(() -> yItem.getResultLabel().setText(yResult));
                Platform.runLater(() -> xItem.getResultLabel().setText(xResult));

                String[] xArray = xResult.substring(1, xResult.length() - 1).split(",");
                String[] yArray = yResult.substring(1, yResult.length() - 1).split(",");
                log.info("{} station function item array length: X={}, Y={}", station.getName(), xArray.length, yArray.length);

                for (int i1 = 0; i1 < xArray.length; i1++) {
                    testModel.addTestItem(xArray[i1], yItem.getName(), yArray[i1], "N", "OK");
                }

                // Upload function item test data to MES
                String testDataJson = JSONObject.toJSONString(testModel);
                log.info("{} station uploading function item test data to MES: {}", station.getName(), testDataJson);
                boolean success = ApiService.sendTestData(testDataJson);
                log.info("{} station function item test data upload result: {}", station.getName(), success ? "success" : "failure");

                allTestSuccess = allTestSuccess && success;
                log.info("{} station completed processing function item: {}, time: {}", station.getName(), nextItem.getName(), DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
                testModel.clearData();
            }
        }

        // If need to call outbound interface and all tests are successful
        if (callOutPass && allTestSuccess && hasProcessedItems && !hasCalledOutPass) {
            log.info("{} station starts processing outbound operation...", station.getName());
            PassData outPassData = new PassData();
            outPassData.setNodeCode(station.getProcess());
            outPassData.setStationCode(station.getName());
            outPassData.setSn(testModel.getSn());
            outPassData.setLineCode(testModel.getLineCode());
            outPassData.setPassType(2); // 2 means outbound
            outPassData.setPassTime(DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
            String jsonString = JSONObject.toJSONString(outPassData);

            log.info("{} station outbound interface request data: {}", station.getName(), jsonString);
            boolean outPassSuccess = sendPassData(jsonString);
            log.info("{} station outbound interface call result: {}", station.getName(), outPassSuccess ? "success" : "failure");

            // Mark outbound interface as called to avoid duplicate calls
            if (outPassSuccess) {
                hasCalledOutPass = true;
                log.info("{} station outbound operation processing completed", station.getName());
            } else {
                log.error("{} station outbound operation failed", station.getName());
            }
        }

        // If found write item, write corresponding value based on test result
        if (writeItem != null && hasProcessedItems) {
            String writeValue = allTestSuccess ? "1" : "2"; // 1 means OK, 2 means NG
            log.info("{} station test operation write PLC result: {}", station.getName(), writeValue);
            writeItemValue(writeItem, writeValue);
        } else if (writeItem == null) {
            log.warn("{} station test operation failed: No corresponding write item found", station.getName());
        }

        log.info("{} station test operation processing completed, test result: {}", station.getName(), allTestSuccess ? "all successful" : "some failures exist");
    }

    private void handleBindSnOperation(StationItem writeItem) {
        String result = "";
        if (hasBindSn) {
            log.info("has bind sn");
            return;
        }
        for (StationItem item : station.getItems()) {
            // 处理SN项
            if ("SN项".equals(item.getItemType())) {
                result = readItemValue(item);
                log.info("Read sn:  " + result);
                final String finalResult = result;
                Platform.runLater(() -> item.getResultLabel().setText(finalResult));
            }
        }
        if (writeItem != null && StringUtils.isNotEmpty(result)) {

            boolean success = ApiService.sendBindSnData(result);

            // 根据接口返回结果写入相应的值（OK/NG）
            String writeValue = success ? "1" : "2"; // 1表示OK，2表示NG
            writeItemValue(writeItem, writeValue);

        } else {
            log.error("find writeItem error or sn is empty");
        }
    }


    /**
     * 处理复位操作
     */
    private void handleResetOperation(StationItem writeItem) {
        log.info("{} station starts processing reset operation...", station.getName());
        if (writeItem != null) {
            // Clear write result
            String writeValue = "0";
            log.info("{} station reset operation write PLC value: {}", station.getName(), writeValue);
            writeItemValue(writeItem, writeValue);
            log.info("{} station reset operation processing completed", station.getName());
        } else {
            log.warn("{} station reset operation failed: No corresponding write item found", station.getName());
        }
    }

    /**
     * 处理心跳项 - 当心跳项值为1时立即复位为0
     * 心跳项处理独立于其他项，不影响其他项的处理逻辑
     */

    private String readItemValue(StationItem item) {
        String address = item.getDb() + item.getOffset();
        try {
            String dataType = item.getDataType().toLowerCase();
            String result = "";

            // 处理数组类型
            if (dataType.startsWith("array[")) {
                String elementType = parseArrayElementType(dataType);
                int arrayLength = parseArrayLength(dataType);

                if ("real".equals(elementType)) {
                    result = String.valueOf(s7PLC.readFloat32(generateAddress(item.getDb(), item.getOffset(), "real", arrayLength)));
                } else if ("bool".equals(elementType)) {
                    result = String.valueOf(s7PLC.readBoolean(generateAddress(item.getDb(), item.getOffset(), "bool", arrayLength)));
                } else {
                    result = "Unsupported array element type: " + elementType;
                }
            }
            // Process basic type
            else {
                switch (dataType) {
                    case "bool":
                        result = String.valueOf(s7PLC.readBoolean(address));
                        break;
                    case "byte":
                        result = String.valueOf(s7PLC.readByte(address));
                        break;
                    case "short":
                        result = String.valueOf(s7PLC.readInt16(address));
                        break;
                    case "int":
                        result = String.valueOf(s7PLC.readInt32(address));
                        break;
                    case "float":
                        result = String.valueOf(s7PLC.readFloat32(address));
                        break;
                    case "string":
                        result = s7PLC.readString(address, item.getLength());
                        break;
                    default:
                        result = "Unsupported type: " + dataType;
                        break;
                }
            }
            return result;
        } catch (Exception e) {
            String errorMsg = "Error: " + e.getMessage();
            return errorMsg;
        }
    }

    private List<String> generateAddress(String db, double address0, String type, int length) {
        List<String> result = new ArrayList<>();
        switch (type) {
            case "real":
                for (int i = 0; i < length; i++) {
                    result.add(db + (address0 + i * 4));
                }
                break;
            case "bool":
                for (int i = 0; i < length; i++) {
                    result.add(db + (address0 + (int) (0.1 * i / 0.8) + (0.1 * i % 0.8)));
                }
                break;
        }

        return result;
    }

    /**
     * 从数组类型字符串中解析数组长度
     * 例如：从 "array[0..50] of real" 解析出长度 51
     *
     * @param dataType 数据类型字符串
     * @return 数组长度
     */
    private int parseArrayLength(String dataType) {
        try {
            // 提取 [0..N] 部分
            int startIndex = dataType.indexOf("[");
            int endIndex = dataType.indexOf("]");
            if (startIndex >= 0 && endIndex > startIndex) {
                String rangeStr = dataType.substring(startIndex + 1, endIndex);
                // 提取上限值 N
                String[] rangeParts = rangeStr.split("\\.\\.");
                if (rangeParts.length == 2) {
                    int startRange = Integer.parseInt(rangeParts[0]);
                    int endRange = Integer.parseInt(rangeParts[1]);
                    // 数组长度为上限+1（因为包含0）
                    return endRange - startRange;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 默认返回一个安全值
        return 1;
    }

    /**
     * 从数组类型字符串中解析元素类型
     * 例如：从 "array[0..50] of real" 解析出 "real"
     *
     * @param dataType 数据类型字符串
     * @return 元素类型
     */
    private String parseArrayElementType(String dataType) {
        try {
            int ofIndex = dataType.indexOf(" of ");
            if (ofIndex >= 0) {
                return dataType.substring(ofIndex + 4).trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private boolean sendTestData(String testData) {
        log.info("{}工位发送测试数据到MES接口，数据内容: {}", station.getName(), testData);
        boolean result = ApiService.sendTestData(testData);
        log.info("{}工位测试数据发送结果: {}", station.getName(), result ? "成功" : "失败");
        return result;
    }

    /**
     * 发送上报数据到接口
     *
     * @param passData 需要上报的数据
     * @return 上报是否成功
     */
    private boolean sendPassData(String passData) {
        log.info("{}sendPassData， passData: {}", station.getName(), passData);
        boolean result = ApiService.sendPassData(passData);
        log.info("{}sendPassData: {}", station.getName(), result ? "success" : "fail");
        return result;
    }

    /**
     * 发送函数数据到接口（x和y坐标）
     *
     * @param xData x坐标数据
     * @param yData y坐标数据
     * @return 上报是否成功
     */
    private boolean sendFunctionData(String xData, String yData) {
        log.info("{}工位发送函数数据到MES接口，X数据: {}, Y数据: {}", station.getName(), xData, yData);
        boolean result = ApiService.sendFunctionData(xData, yData);
        log.info("{}工位函数数据发送结果: {}", station.getName(), result ? "成功" : "失败");
        return result;
    }

    private void writeItemValue(StationItem item, String value) {
        String address = item.getDb() + item.getOffset();
        try {
            switch (item.getDataType().toLowerCase()) {
                case "bool":
                    s7PLC.writeBoolean(address, Boolean.parseBoolean(value));
                    break;
                case "byte":
                    s7PLC.writeByte(address, Byte.parseByte(value));
                    break;
                case "short":
                    s7PLC.writeInt16(address, Short.parseShort(value));
                    break;
                case "int":
                    s7PLC.writeInt32(address, Integer.parseInt(value));
                    break;
                case "float":
                    s7PLC.writeFloat32(address, Float.parseFloat(value));
                    break;
                case "string":
                    s7PLC.writeString(address, value);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported type for writing: " + item.getDataType());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}