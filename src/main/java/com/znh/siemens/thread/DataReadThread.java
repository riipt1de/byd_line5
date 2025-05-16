package com.znh.siemens.thread;

import com.github.xingshuangs.iot.protocol.s7.service.S7PLC;
import com.znh.siemens.utils.FinalConstant;
import javafx.application.Platform;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TextArea;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author JayNH
 * @Description TODO
 * @Date 2023/5/5 11:44
 * @Version 1.0
 */
@Slf4j
public class DataReadThread extends Thread {

    protected S7PLC s7PLC;
    public volatile boolean isRunning = true;
    public boolean isStartCurveShader = false;
    private String dataType;
    private String offset;
    private int stringLength;
    public int shaderCount = 5;
    private String timeType;
    private int readInterval;
    private TextArea readResult;
    private XYChart.Series<CategoryAxis, NumberAxis> series = new XYChart.Series<>();
    protected SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private final AtomicBoolean cleaned = new AtomicBoolean(false);

    public DataReadThread(){}

    public DataReadThread(S7PLC s7PLC, String dataType, String offset, Integer stringLength, String timeType, int readInterval, TextArea readResult, XYChart.Series<CategoryAxis, NumberAxis> series){
        this.s7PLC = s7PLC;
        this.dataType = dataType;
        this.offset = offset;
        this.stringLength = stringLength;
        this.timeType = timeType;
        this.readInterval = readInterval;
        this.readResult = readResult;
        this.series = series;
        
        // 设置线程为守护线程，这样当主程序退出时，线程会自动终止
        setDaemon(true);
        // 设置线程名称，方便调试
        setName("DataRead-" + offset + "-Thread");
    }

    @Override
    public void run() {
        try{
            log.info("----------读取线程开启----------" + Thread.currentThread().getName());
            String resultData = "";
            while(isRunning){
                if(s7PLC == null || !s7PLC.checkConnected()){
                    log.warn("PLC连接已断开，读取线程将终止");
                    isRunning = false;
                    break;
                }
                
                try {
                    switch(dataType){
                        case "Bool":
                            boolean booleanResult = s7PLC.readBoolean(offset);
                            resultData = Boolean.toString(booleanResult);
                            break;
                        case "Byte":
                            byte byteResult = s7PLC.readByte(offset);
                            resultData = Byte.toString(byteResult);
                            break;
                        case "Short":
                            short shortResult = s7PLC.readInt16(offset);
                            resultData = Short.toString(shortResult);
                            break;
                        case "UShort":
                            int uShortResult = s7PLC.readUInt16(offset);
                            resultData = Integer.toString(uShortResult);
                            break;
                        case "Int":
                        case "Long":
                            int intResult = s7PLC.readInt32(offset);
                            resultData = Integer.toString(intResult);
                            break;
                        case "UInt":
                        case "ULong":
                            long uIntResult = s7PLC.readUInt32(offset);
                            resultData = Long.toString(uIntResult);
                            break;
                        case "Float":
                            float floatResult = s7PLC.readFloat32(offset);
                            resultData = Float.toString(floatResult);
                            break;
                        case "Double":
                            double doubleResult = s7PLC.readFloat32(offset);
                            resultData = Double.toString(doubleResult);
                            break;
                        case "String":
                            resultData = s7PLC.readString(offset,stringLength);
                            break;
                        default:
                            resultData = "Unknown data type: " + dataType;
                            break;
                    }
                    
                    final String finalResultData = resultData;
                    final long currentTime = System.currentTimeMillis();
                    
                    // 使用Platform.runLater更新UI，但要检查线程是否仍在运行和TextArea是否有效
                    if (isRunning && readResult != null) {
                        Platform.runLater(() -> {
                            try {
                                readResult.setText(simpleDateFormat.format(currentTime) + " : " + finalResultData);
                            } catch (Exception e) {
                                log.error("更新UI时发生异常: {}", e.getMessage(), e);
                            }
                        });
                    }
                    
                    // 判断是否开启曲线渲染数据
                    if(isStartCurveShader && isRunning){
                        try {
                            // 如果数据是数字类型，则可以添加到图表数据系列中
                            float dataValue = Float.parseFloat(finalResultData);
                            
                            Platform.runLater(() -> {
                                try {
                                    if(series.getData().size() == shaderCount){
                                        series.getData().remove(0);
                                    }
                                    series.getData().add(new XYChart.Data(simpleDateFormat.format(currentTime), dataValue));
                                } catch (Exception e) {
                                    log.error("更新图表数据时发生异常: {}", e.getMessage(), e);
                                }
                            });
                        } catch (NumberFormatException e) {
                            // 如果数据不是有效的浮点数，则忽略图表更新
                            log.warn("无法将数据转换为浮点数进行图表显示: {}", finalResultData);
                        }
                    }
                    
                    try {
                        if (timeType.equals(FinalConstant.MS)) {
                            TimeUnit.MILLISECONDS.sleep(readInterval);
                        } else {
                            TimeUnit.SECONDS.sleep(readInterval);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("读取线程被中断");
                        break;
                    }
                } catch (Exception e) {
                    log.error("数据读取过程中发生异常: {}", e.getMessage(), e);
                    
                    // 出现异常时，显示错误信息
                    if (isRunning && readResult != null) {
                        final String errorMessage = "Error: " + e.getMessage();
                        Platform.runLater(() -> {
                            try {
                                readResult.setText(simpleDateFormat.format(System.currentTimeMillis()) + " : " + errorMessage);
                            } catch (Exception ex) {
                                log.error("更新错误信息到UI时发生异常: {}", ex.getMessage(), ex);
                            }
                        });
                    }
                    
                    // 异常后等待一段时间再重试，避免CPU过高
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("读取线程运行时发生未捕获的异常: {}", e.getMessage(), e);
        } finally {
            // 确保在线程结束时清理资源
            cleanup();
            log.info("----------读取线程结束----------" + Thread.currentThread().getName());
        }
    }
    
    /**
     * 清理线程使用的资源
     */
    public void cleanup() {
        if (cleaned.compareAndSet(false, true)) {
            log.info("开始清理读取线程资源");
            
            try {
                // 清除图表数据
                if (series != null && series.getData() != null) {
                    Platform.runLater(() -> {
                        try {
                            series.getData().clear();
                        } catch (Exception e) {
                            log.error("清理图表数据时发生异常: {}", e.getMessage(), e);
                        }
                    });
                }
                
                // 不要清除共享资源的引用，如s7PLC
                
                // 为GC提示
                System.gc();
                
                log.info("读取线程资源清理完成");
            } catch (Exception e) {
                log.error("清理读取线程资源时发生异常: {}", e.getMessage(), e);
            }
        }
    }
}
