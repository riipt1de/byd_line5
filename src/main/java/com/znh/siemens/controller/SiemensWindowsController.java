package com.znh.siemens.controller;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.xingshuangs.iot.protocol.s7.enums.EPlcType;
import com.github.xingshuangs.iot.protocol.s7.service.S7PLC;
import com.znh.siemens.model.*;
import com.znh.siemens.thread.DataReadThread;
import com.znh.siemens.thread.StationDataReadThread;
import com.znh.siemens.utils.*;
import com.znh.siemens.view.SiemensWindowsView;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @Author JayNH
 * @Description TODO
 * @Date 2023/5/5 11:08
 * @Version 1.0
 */
@Slf4j
public class SiemensWindowsController extends SiemensWindowsView {

    private S7PLC s7PLC;
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    protected DataReadThread dataReadThread;
    protected StationDataReadThread stationDataThread;
    protected XYChart.Series<CategoryAxis, NumberAxis> series = new XYChart.Series<>();
    @FXML
    private ListView<CheckBox> stationListView;

    @FXML
    private ComboBox<String> plcAccountComboBox;

    @FXML
    private CheckBox selectAllCheckBox;
    @FXML
    private Button refreshStationsButton;
    private List<Station> stations = new ArrayList<>();
    @FXML
    private GridPane stationItemGrid;
    @FXML
    private TextField stationReadTime;
    @FXML
    private ChoiceBox<String> stationTimeChoiceBox;
    @FXML
    private Button stationReadButton;
    @FXML
    private Button stationStopButton;
    @FXML
    private GridPane stationDataGrid;

    private Map<Station, StationDataReadThread> stationThreads = new HashMap<>();
    private List<Station> selectedStations = new ArrayList<>();

    public static FXMLLoader getFxmlLoader() {
        URL url = SiemensWindowsController.class.getResource("/fxml/SiemensWindow.fxml");
        return new FXMLLoader(url);
    }

    private TighteningController tighteningController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        modelComboBox.getItems().addAll("200", "200SMART", "300", "400", "1200", "1500");
        modelComboBox.setValue("200SMART");
        timeChoiceBox.getItems().addAll("ms", "s");
        timeChoiceBox.setValue("ms");
        dataLineChart.getData().add(series);

        // 初始化工位数据
        initializeStations();

        // 设置工位列表视图
        initializeStationListView();

        stationTimeChoiceBox.getItems().addAll("ms", "s");
        stationTimeChoiceBox.setValue("ms");

        // 初始化按钮状态
        stationReadButton.setDisable(true);
        stationStopButton.setDisable(true);
        
        // 设置全选复选框的事件处理器
        selectAllCheckBox.setOnAction(this::onSelectAllAction);
        
        // 初始化并启动拧紧控制器HTTP服务器
//        tighteningController = new TighteningController();
//        tighteningController.startServer();
        // 初始化PLC模型下拉框
        modelComboBox.setItems(FXCollections.observableArrayList("200", "200SMART", "300", "400", "1200", "1500"));
        modelComboBox.setValue("1200");

        // 初始化PLC账号下拉框
        initializePlcAccountComboBox();
    }
    
    /**
     * 初始化工位列表视图，为每个工位创建一个带复选框的项
     */
    private void initializeStationListView() {
        stationListView.getItems().clear();
        
        // 按工位名称排序
        stations.sort((s1, s2) -> s1.getName().compareTo(s2.getName()));
        
        for (Station station : stations) {
            CheckBox checkBox = new CheckBox(station.getName() + " | " + station.getProcess());
            checkBox.setUserData(station); // 将Station对象存储在CheckBox的userData中
            checkBox.setOnAction(event -> onStationCheckBoxChanged());
            stationListView.getItems().add(checkBox);
        }
    }

    private void initializeStations() {
        try {
            // 从default_stations.json文件读取工位数据
            File dataDir = new File("data");
            File file = new File(dataDir, "default_stations.json");

            if (file.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                List<Station> loadedStations = mapper.readValue(file, mapper.getTypeFactory().constructCollectionType(List.class, Station.class));

                if (loadedStations != null && !loadedStations.isEmpty()) {
                    stations.addAll(loadedStations);
                    return;
                }
            }

            // 如果文件不存在或读取失败，创建硬编码的工位作为备选
            Station op11 = new Station("OP11","OP11");
            op11.addItem("设备ID","db9000.", 0.00, "string", 12);
            op11.addItem("工位ID","db9000.", 14.00, "string", 12);
            op11.addItem("工序零件身份证", "db9000.",28.00, "string", 24);
            op11.addItem("PLC请求清洗MES读取油路板码", "db9000.",54.00, "bool", 1);
            op11.addItem("油路板到位拍照检测","db9000.", 55.00, "byte", 1);
            op11.addItem("MES读取油路板完成", "db9000.",56.00, "byte", 1);
            stations.add(op11);

            // 创建OP12工位
            Station op12 = new Station("OP12","OP12");
            op12.addItem("设备ID_1","db9000.", 58.00, "string", 12);
            op12.addItem("工位 ID_1","db9000.", 72.00, "string", 12);
            op12.addItem("工序零件身份证_1","db9000.", 86.00, "string", 24);
            op12.addItem("PLC请求MES单向阀阀芯混料校验","db9000.", 112.00, "byte", 1);
            op12.addItem("MES单向阀阀芯混料校验结果","db9000.", 113.00, "byte", 1);
            op12.addItem("PLC请求MES读取冷却器单向阀安装到位","db9000.", 114.00, "byte", 1);
            op12.addItem("冷却器单向阀安装到位检测", "db9000.",116.00, "float", 1);
            op12.addItem("MES读取完成", "db9000.",120.00, "bool", 1);
            op12.addItem("PLC请求MES冷却器单向阀弹簧混料校验","db9000.", 121.00, "byte", 1);
            op12.addItem("MES冷却器单向阀弹簧混料校验结果", "db9000.",122.00, "byte", 1);
            op12.addItem("PLC请求MES主油路单向阀弹簧混料校验","db9000.", 123.00, "byte", 1);
            op12.addItem("MES主油路单向阀弹簧混料结果", "db9000.",124.00, "byte", 1);
            op12.addItem("PLC请求MES读取冷却器单向阀弹簧压力曲线","db9000.", 125.00, "byte", 1);
            op12.addItem("冷却器单向阀弹簧压力曲线检测","db9000.", 126.00, "array[0..10] of real", 1);
            op12.addItem("MES读取完成_1", "db9000.",170.00, "array[0..10] of bool", 1);
            op12.addItem("PLC请求MES主油路单向阀弹簧压力曲线","db9000.", 172.00, "byte", 1);
            op12.addItem("主油路单向阀弹簧压力曲线检测","db9000.", 174.00, "array[0..10] of real", 1);
            op12.addItem("MES读取完成_2", "db9000.",218.00, "array[0..10] of bool", 1);
            op12.addItem("PLC请求MES读取堵头密封圈安装到位/密封圈缺陷","db9000.", 220.00, "byte", 1);
            op12.addItem("堵头密封圈安装到位/密封圈缺陷检测","db9000.", 221.00, "byte", 1);
            op12.addItem("MES读取完成_3", "db9000.",222.00, "bool", 1);
            op12.addItem("PLC请求MES堵头安装到位","db9000.", 222.10, "bool", 1);
            op12.addItem("堵头安装到位检测", "db9000.",224.00, "float", 1);
            op12.addItem("MES读取完成_4","db9000.", 228.00, "bool", 1);
            op12.addItem("PLC请求MES挡板安装到位", "db9000.",229.00, "byte", 1);
            op12.addItem("挡板安装到位检测","db9000.", 230.00, "byte", 1);
            op12.addItem("MES读取完成_5", "db9000.",231.00, "bool", 1);
            stations.add(op12);

            // 创建OP21工位
            Station op21 = new Station("OP21","OP21");
            op21.addItem("设备ID_2","db9000.", 232.00, "string", 12);
            op21.addItem("工位 ID_2","db9000.", 246.00, "string", 12);
            op21.addItem("工序零件身份证_2","db9000.", 260.00, "string", 24);
            op21.addItem("PLC请求读取销钉安装位置（间距）","db9000.", 286.00, "byte", 1);
            op21.addItem("销钉安装位置检测（间距）","db9000.", 288.00, "float", 1);
            op21.addItem("MES读取完成_6","db9000.", 292.00, "bool", 1);
            op21.addItem("PLC请求MES读取销钉深度结果","db9000.", 293.00, "byte", 1);
            op21.addItem("销钉深度检测", "db9000.",294.00, "float", 1);
            op21.addItem("MES读取完成_7", "db9000.",298.00, "bool", 1);
            stations.add(op21);

            //创建工位OP22
            Station op22 = new Station("OP22","OP22");
            op22.addItem("设备ID_3", "db9000.",300.00, "string", 12);
            op22.addItem("工位 ID_3", "db9000.",314.00, "string", 12);
            op22.addItem("工序零件身份证_3", "db9000.",328.00, "string", 24);
            op22.addItem("PLC请求MES轮端油泵单向阀弹簧混料校验", "db9000.",354.00, "byte", 1);
            op22.addItem("MES校验完成", "db9000.",355.00, "byte", 1);
            op22.addItem("PLC请求溢流阀弹簧混料校验", "db9000.",356.00, "byte", 1);
            op22.addItem("MES校验完成_1", "db9000.",357.00, "byte", 1);
            op22.addItem("PLC请求MES读取轮端油泵单向阀弹簧压力曲线", "db9000.",358.00, "byte", 1);
            op22.addItem("轮端油泵单向阀弹簧压力曲线检测", "db9000.",360.00, "array[0..10] of real", 1);
            op22.addItem("MES读取完成_8", "db9000.",404.00, "array[0..10] of bool", 1);
            op22.addItem("PLC请求读取溢流阀弹簧压力曲线","db9000.", 406.00, "byte", 1);
            op22.addItem("溢流阀弹簧压力曲线检测", "db9000.",408.00, "array[0..10] of real", 1);
            op22.addItem("MES读取完成_9", "db9000.",452.00, "array[0..10] of bool", 1);
            op22.addItem("PLC请求读取堵头密封圈安装到位/密封圈缺陷", "db9000.",454.00, "byte", 1);
            op22.addItem("堵头密封圈安装到位/密封圈缺陷检测_1", "db9000.",455.00, "byte", 1);
            op22.addItem("MES读取完成_10", "db9000.",456.00, "bool", 1);
            op22.addItem("PLC请求MES读取堵头安装到位", "db9000.",457.00, "byte", 1);
            op22.addItem("堵头安装到位检测_1", "db9000.",458.00, "float", 1);
            op22.addItem("MES读取完成_11", "db9000.",462.00, "bool", 1);
            op22.addItem("PLC请求MES读取挡板安装到位","db9000.", 463.00, "byte", 1);
            op22.addItem("挡板安装到位检测_1", "db9000.",464.00, "byte", 1);
            op22.addItem("MES读取完成_12", "db9000.",465.00, "bool", 1);
            stations.add(op22);

            //创建OP23
            Station op23 = new Station("OP23","OP23");
            op23.addItem("设备ID_4", "db9000.",466.00, "string", 12);
            op23.addItem("工位 ID_4", "db9000.",480.00, "string", 12);
            op23.addItem("工序零件身份证_4", "db9000.",494.00, "string", 24);
            op23.addItem("PLC请求MES发电机阀芯混料校验", "db9000.",520.00, "byte", 1);
            op23.addItem("MES校验完成_2", "db9000.",521.00, "byte", 1);
            op23.addItem("PLC请求MES读取发电机阀芯安装到位", "db9000.",522.00, "byte", 1);
            op23.addItem("发电机阀芯安装到位检测", "db9000.",524.00, "float", 1);
            op23.addItem("MES读取完成_13", "db9000.",528.00, "bool", 1);
            op23.addItem("PLC请求MES发电机弹簧混料校验", "db9000.",529.00, "byte", 1);
            op23.addItem("MES校验完成_3", "db9000.",530.00, "byte", 1);
            op23.addItem("PLC请求MES主油路弹簧混料校验", "db9000.",531.00, "byte", 1);
            op23.addItem("MES校验完成_4", "db9000.",532.00, "byte", 1);
            op23.addItem("PLC请求MES读取发电机弹簧压力曲线检测", "db9000.",533.00, "byte", 1);
            op23.addItem("发电机弹簧压力曲线检测", "db9000.",534.00, "array[0..10] of real", 1);
            op23.addItem("MES读取完成_14", "db9000.",578.00, "array[0..10] of bool", 1);
            op23.addItem("PLC请求MES读取主油路弹簧压力曲线","db9000.", 580.00, "byte", 1);
            op23.addItem("主油路弹簧压力曲线检测", "db9000.",582.00, "array[0..10] of real", 1);
            op23.addItem("MES读取完成_15", "db9000.",626.00, "array[0..10] of bool", 1);
            op23.addItem("PLC请求MES主油路阀芯混料校验", "db9000.",628.00, "byte", 1);
            op23.addItem("MES校验完成_5", "db9000.",629.00, "byte", 1);
            op23.addItem("PLC请求MES读取主油路阀芯安装到位", "db9000.",630.00, "byte", 1);
            op23.addItem("主油路阀芯安装到位检测", "db9000.",632.00, "float", 1);
            op23.addItem("MES读取完成_16", "db9000.",636.00, "bool", 1);
            op23.addItem("PLC请求MES读取堵头密封圈安装到位/密封圈缺陷_1", "db9000.",637.00, "byte", 1);
            op23.addItem("堵头密封圈安装到位/密封圈缺陷检测_2", "db9000.",638.00, "byte", 1);
            op23.addItem("MES读取完成_17", "db9000.",639.00, "bool", 1);
            op23.addItem("PLC请求MES读取堵头安装到位_1", "db9000.",640.00, "byte", 1);
            op23.addItem("堵头安装到位检测_2", "db9000.",642.00, "float", 1);
            op23.addItem("MES读取完成_18", "db9000.",646.00, "bool", 1);
            op23.addItem("PLC请求MES读取挡板安装到位_1", "db9000.",647.00, "byte", 1);
            op23.addItem("挡板安装到位检测_2", "db9000.",648.00, "byte", 1);
            op23.addItem("MES读取完成_19", "db9000.",649.00, "bool", 1);
            stations.add(op23);

            //创建OP31工位
            Station op31 = new Station("OP31","OP31");
            op31.addItem("设备ID_5", "db9000.",650.00, "string", 12);
            op31.addItem("工位 ID_5", "db9000.",664.00, "string", 12);
            op31.addItem("工序零件身份证_5", "db9000.",678.00, "string", 24);
            op31.addItem("PLC请求MES读取上下油路板扫码并绑定", "db9000.",704.00, "byte", 1);
            op31.addItem("上下油路板扫码绑定", "db9000.",706.00, "array[0..2]of string[24]", 1);
            op31.addItem("MES读取并绑定完成", "db9000.",784.00, "bool", 1);
            stations.add(op31);

            //创建OP32工位
            Station op32 = new Station("OP32","OP32");
            op32.addItem("设备ID_6", "db9000.",786.00, "string", 12);
            op32.addItem("工位 ID_6", "db9000.",800.00, "string", 12);
            op32.addItem("工序零件身份证_6", "db9000.",814.00, "string", 24);
            op32.addItem("PLC请求MES缓冲器弹簧混料校验", "db9000.",840.00, "byte", 1);
            op32.addItem("MES校验完成_6", "db9000.",841.00, "byte", 1);
            op32.addItem("PLC请求MES读取缓冲器弹簧压力曲线检测", "db9000.",842.00, "byte", 1);
            op32.addItem("缓冲器弹簧压力曲线检测", "db9000.",844.00, "array[0..10] of real", 1);
            op32.addItem("MES读取完成_20", "db9000.",888.00, "array[0..10] of bool", 1);
            op32.addItem("PLC请求MES读取", "db9000.",890.00, "byte", 1);
            op32.addItem("外转子1正反防呆检测", "db9000.",891.00, "byte", 1);
            op32.addItem("MES读取完成_21", "db9000.",892.00, "bool", 1);
            op32.addItem("PLC请求MES读取_1", "db9000.",893.00, "byte", 1);
            op32.addItem("外转子1安装到位检测", "db9000.",894.00, "float", 1);
            op32.addItem("MES读取完成_22", "db9000.",898.00, "bool", 1);
            op32.addItem("PLC请求MES读取_2", "db9000.",899.00, "byte", 1);
            op32.addItem("外转子2正反防呆检测", "db9000.",900.00, "byte", 1);
            op32.addItem("MES读取完成_23", "db9000.",901.00, "bool", 1);
            op32.addItem("PLC请求MES读取_3", "db9000.",902.00, "byte", 1);
            op32.addItem("外转子2安装到位检测", "db9000.",904.00, "float", 1);
            op32.addItem("MES读取完成_24", "db9000.",908.00, "bool", 1);
            op32.addItem("PLC请求MES读取_4", "db9000.",909.00, "byte", 1);
            op32.addItem("内转子1正反防呆检", "db9000.",910.00, "byte", 1);
            op32.addItem("MES读取完成_25", "db9000.",911.00, "bool", 1);
            op32.addItem("PLC请求MES读取_5", "db9000.",912.00, "byte", 1);
            op32.addItem("内转子1安装到位检测", "db9000.",914.00, "float", 1);
            op32.addItem("MES读取完成_26", "db9000.",918.00, "bool", 1);
            op32.addItem("PLC请求MES读取_6", "db9000.",919.00, "byte", 1);
            op32.addItem("内转子2正反防呆检测", "db9000.",920.00, "byte", 1);
            op32.addItem("MES读取完成_27", "db9000.",921.00, "bool", 1);
            op32.addItem("PLC请求MES读取_7", "db9000.",922.00, "byte", 1);
            op32.addItem("内转子2安装到位检测", "db9000.",924.00, "float", 1);
            op32.addItem("MES读取完成_28", "db9000.",928.00, "bool", 1);
            op32.addItem("PLC请求MES读取_8", "db9000.",929.00, "bool", 1);
            op32.addItem("3D视觉检测内转子，外转子， 下油路板三者结合面高度差", "db9000.",930.00, "array[0..10] of real", 1);
            op32.addItem("MES读取完成_29", "db9000.",974.00, "bool", 1);
            stations.add(op32);

            //创建OP33工位
            Station op33 = new Station("OP33","OP33");
            op33.addItem("设备ID_7", "db9000.",976.00, "string", 12);
            op33.addItem("工位 ID_7", "db9000.",990.00, "string", 12);
            op33.addItem("工序零件身份证_7", "db9000.",1004.00, "string", 24);
            op33.addItem("PLC请求MES判断弹簧混料校验", "db9000.",1030.00, "byte", 1);
            op33.addItem("MES校验完成_7", "db9000.",1031.00, "byte", 1);
            op33.addItem("PLC请求MES读取弹簧压力曲线", "db9000.",1032.00, "byte", 1);
            op33.addItem("弹簧压力曲线检测", "db9000.",1034.00, "array[0..10] of real", 1);
            op33.addItem("MES读取完成_30", "db9000.",1078.00, "array[0..10] of bool", 1);
            op33.addItem("PLC请求MES判断单向阀混料校验", "db9000.",1080.00, "byte", 1);
            op33.addItem("MES校验完成_8", "db9000.",1081.00, "byte", 1);
            op33.addItem("PLC请求MES读取单向阀安装到位", "db9000.",1082.00, "byte", 1);
            op33.addItem("单向阀安装到位检测", "db9000.",1084.00, "float", 1);
            op33.addItem("MES读取完成_31", "db9000.",1088.00, "bool", 1);
            stations.add(op33);

            //创建工位OP34
            Station op34 = new Station("OP34","OP34");
            op34.addItem("设备ID_8", "db9000.",1090.00, "string", 12);
            op34.addItem("工位 ID_8", "db9000.",1104.00, "string", 12);
            op34.addItem("工序零件身份证_8", "db9000.",1118.00, "string", 24);
            op34.addItem("PLC请求MES读取通孔检测", "db9000.",1144.00, "byte", 24);
            op34.addItem("通孔检测", "db9000.",1145.00, "byte", 24);
            op34.addItem("MES读取完成_32", "db9000.",1146.00, "bool", 24);
            stations.add(op34);

            //创建OP35工位
            Station op35 = new Station("OP35","OP35");
            op35.addItem("设备ID_9", "db9000.",1148.00, "string", 12);
            op35.addItem("工位 ID_9", "db9000.",1162.00, "string", 12);
            op35.addItem("工序零件身份证_9", "db9000.",1176.00, "string", 24);
            op35.addItem("PLC请求MES读取堵头安装到位检测", "db9000.",1202.00, "byte", 1);
            op35.addItem("堵头安装到位检测", "db9000.",1203.00, "byte", 1);
            op35.addItem("MES读取完成_33", "db9000.",1204.00, "bool", 1);
            op35.addItem("PLC请求MES读取堵头掉落检测", "db9000.",1205.00, "byte", 1);
            op35.addItem("堵头掉落检测", "db9000.",1206.00, "byte", 1);
            op35.addItem("MES读取完成_34", "db9000.",1207.00, "bool", 1);
            stations.add(op35);

            //创建工位OP36
            Station op36 = new Station("OP36","OP36");
            op36.addItem("设备ID_10", "db9000.",1208.00, "string", 12);
            op36.addItem("工位 ID_10", "db9000.",1222.00, "string", 12);
            op36.addItem("工序零件身份证_10", "db9000.",1236.00, "string", 24);
            op36.addItem("PLC请求MES读取产品码", "db9000.",1262.00, "byte", 1);
            op36.addItem("产品码", "db9000.",1264.00, "string", 24);
            op36.addItem("MES读取完成_35", "db9000.",1290.00, "bool", 1);
            op36.addItem("PLC请求MES读取拧紧螺丝顺序号", "db9000.",1291.00, "byte", 1);
            op36.addItem("MES读取完成_36", "db9000.",1292.00, "bool", 1);
            op36.addItem("MES下发程序号给PLC", "db9000.",1293.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC", "db9000.",1294.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_1", "db9000.",1295.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_2", "db9000.",1296.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_3", "db9000.",1297.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_4", "db9000.",1298.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_5", "db9000.",1299.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_6", "db9000.",1300.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_7", "db9000.",1301.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_8", "db9000.",1302.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_9", "db9000.",1303.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_34", "db9000.",1304.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_10", "db9000.",1305.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_35", "db9000.",1306.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_36", "db9000.",1307.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_11", "db9000.",1308.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_37", "db9000.",1309.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_38", "db9000.",1310.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_12", "db9000.",1311.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_39", "db9000.",1312.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_40", "db9000.",1313.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_13", "db9000.",1314.00, "byte", 1);
            op36.addItem("MES下发扭矩结果给PLC_41", "db9000.",1315.00, "byte", 1);
            op36.addItem("PLC请求MES螺钉混料校验3", "db9000.",1316.00, "byte", 1);
            op36.addItem("MES校验完成_12", "db9000.",1317.00, "byte", 1);
            op36.addItem("PLC请求MES螺钉混料校验4", "db9000.",1318.00, "byte", 1);
            op36.addItem("MES校验完成_13", "db9000.",1319.00, "byte", 1);
            op36.addItem("PLC请求MES螺钉混料校验5", "db9000.",1320.00, "byte", 1);
            op36.addItem("MES校验完成_14", "db9000.",1321.00, "byte", 1);
            op36.addItem("PLC请求MES螺钉混料校验6", "db9000.",1322.00, "byte", 1);
            op36.addItem("MES校验完成_15", "db9000.",1323.00, "byte", 1);
            op36.addItem("PLC请求MES螺钉混料校验7", "db9000.",1324.00, "byte", 1);
            op36.addItem("MES校验完成_16", "db9000.",1325.00, "byte", 1);
            op36.addItem("PLC请求MES螺钉混料校验8", "db9000.",1326.00, "byte", 1);
            op36.addItem("MES校验完成_17", "db9000.",1327.00, "byte", 1);
            op36.addItem("PLC请求MES螺钉混料校验9", "db9000.",1328.00, "byte", 1);
            op36.addItem("MES校验完成_18", "db9000.",1329.00, "byte", 1);
            stations.add(op36);

            //创建工位OP37
            Station op37 = new Station("OP37","OP37");
            op37.addItem("设备ID_11", "db9000.",1330.00, "string", 12);
            op37.addItem("工位 ID_11", "db9000.",1344.00, "string", 12);
            op37.addItem("工序零件身份证_11", "db9000.",1358.00, "string", 24);
            op37.addItem("PLC请求MES读取通孔检测_1", "db9000.",1384.00, "byte", 1);
            op37.addItem("通孔检测_1", "db9000.",1385.00, "byte", 1);
            op37.addItem("MES读取完成_45", "db9000.",1386.00, "bool", 1);
            op37.addItem("PLC请求MES读取螺钉浮高检测", "db9000.",1387.00, "byte", 1);
            op37.addItem("螺钉浮高检测", "db9000.",1388.00, "byte", 1);
            op37.addItem("MES读取完成_46", "db9000.",1389.00, "bool", 1);
            op37.addItem("PLC请求MES读取转子灵活性检测", "db9000.",1390.00, "byte", 1);
            op37.addItem("转子灵活性检测", "db9000.",1392.00, "float", 1);
            op37.addItem("MES读取完成_47", "db9000.",1396.00, "bool", 1);
            stations.add(op37);

            //创建工位OP38
            Station op38 = new Station("OP38","OP38");
            op38.addItem("设备ID_12", "db9000.",1398.00, "string", 12);
            op38.addItem("工位 ID_12", "db9000.",1412.00, "string", 12);
            op38.addItem("工序零件身份证_12", "db9000.",1426.00, "string", 24);
            op38.addItem("PLC请求MES读取离合器压力阀安装到位", "db9000.",1452.00, "byte", 1);
            op38.addItem("离合器压力阀安装到位检测", "db9000.",1454.00, "float", 1);
            op38.addItem("MES读取完成_48", "db9000.",1458.00, "bool", 1);
            op38.addItem("PLC请求MES读取主压力开关电磁阀安装到位检测", "db9000.",1459.00, "byte", 1);
            op38.addItem("主压力开关电磁阀安装到位检测", "db9000.",1460.00, "float", 1);
            op38.addItem("MES读取完成_49", "db9000.",1464.00, "bool", 1);
            stations.add(op38);

            //创建OP39
            Station op39 = new Station("OP39","OP39");
            op39.addItem("设备ID_13", "db9000.",1466.00, "string", 12);
            op39.addItem("工位 ID_13", "db9000.",1480.00, "string", 12);
            op39.addItem("工序零件身份证_13", "db9000.",1494.00, "string", 24);
            op39.addItem("PLC请求MES读取产品码_3", "db9000.",1520.00, "byte", 1);
            op39.addItem("产品码_3", "db9000.",1522.00, "string", 24);
            op39.addItem("MES读取完成_50", "db9000.",1548.00, "bool", 1);
            op39.addItem("MES读取完成_51", "db9000.",1548.10, "bool", 1);
            op39.addItem("MES下发程序号给PLC_3", "db9000.",1549.00, "byte", 1);
            op39.addItem("MES下发扭矩结果给PLC_14", "db9000.",1550.00, "byte", 1);
            op39.addItem("MES下发扭矩结果给PLC_15", "db9000.",1551.00, "byte", 1);
            op39.addItem("MES下发扭矩结果给PLC_16", "db9000.",1552.00, "byte", 1);
            op39.addItem("MES下发扭矩结果给PLC_17", "db9000.",1553.00, "byte", 1);
            op39.addItem("MES下发扭矩结果给PLC_18", "db9000.",1554.00, "byte", 1);
            op39.addItem("MES下发扭矩结果给PLC_19", "db9000.",1555.00, "byte", 1);
            op39.addItem("MES下发扭矩结果给PLC_20", "db9000.",1556.00, "byte", 1);
            op39.addItem("MES下发扭矩结果给PLC_21", "db9000.",1557.00, "byte", 1);
            op39.addItem("PLC请求MES螺钉混料校验1", "db9000.",1558.00, "byte", 1);
            op39.addItem("PLC请求MES螺钉混料校验1", "db9000.",1558.00, "byte", 1);
            op39.addItem("MES校验完成_9", "db9000.",1559.00, "byte", 1);
            op39.addItem("PLC请求MES螺钉混料校验2", "db9000.",1560.00, "byte", 1);
            op39.addItem("MES校验完成_10", "db9000.",1561.00, "byte", 1);
            stations.add(op39);

            //创建工位OP40
            Station op40 = new Station("OP40","OP40");
            op40.addItem("设备ID_14","db9000.", 1562.00, "string", 12);
            op40.addItem("工位 ID_14", "db9000.",1576.00, "string", 12);
            op40.addItem("工序零件身份证_14", "db9000.",1590.00, "string", 24);
            op40.addItem("PLC请求MES离合器压力传感器混料校验", "db9000.",1616.00, "byte", 1);
            op40.addItem("MES校验完成_11", "db9000.",1617.00, "byte", 1);
            op40.addItem("PLC请求MES读取离合器压力传感器安装到位", "db9000.",1618.00, "byte", 1);
            op40.addItem("离合器压力传感器安装到位检测", "db9000.",1620.00, "float", 1);
            op40.addItem("MES读取完成_37", "db9000.",1624.00, "bool", 1);
            op40.addItem("PLC请求MES读取产品码_1", "db9000.",1625.00, "byte", 1);
            op40.addItem("产品码_1", "db9000.",1626.00, "string", 24);
            op40.addItem("MES读取完成_38", "db9000.",1652.00, "bool", 1);
            op40.addItem("PLC请求MES读取拧紧螺丝顺序号_1", "db9000.",1653.00, "byte", 1);
            op40.addItem("MES读取完成_39", "db9000.",1654.00, "bool", 1);
            op40.addItem("MES下发程序号给PLC_1", "db9000.",1655.00, "byte", 1);
            op40.addItem("MES下发扭矩结果给PLC_22", "db9000.",1656.00, "byte", 1);
            op40.addItem("PLC读取完成", "db9000.",1657.00, "byte", 1);
            op40.addItem("MES下发扭矩结果给PLC_23", "db9000.",1658.00, "byte", 1);
            op40.addItem("MES下发扭矩结果给PLC_42", "db9000.",1659.00, "byte", 1);
            op40.addItem("MES下发扭矩结果给PLC_24", "db9000.",1660.00, "byte", 1);
            stations.add(op40);

            //创建工位OP42
            Station op42 = new Station("OP42","OP42");
            op42.addItem("设备ID_15", "db9000.",1662.00, "string", 12);
            op42.addItem("工位 ID_15", "db9000.",1676.00, "string", 12);
            op42.addItem("工序零件身份证_15", "db9000.",1690.00, "string", 24);
            op42.addItem("PLC请求MES读取产品码_4", "db9000.",1716.00, "byte", 1);
            op42.addItem("MES读取完成_40", "db9000.",1717.00, "bool", 1);
            op42.addItem("MES读取完成_41", "db9000.",1717.10, "bool", 1);
            op42.addItem("MES下发程序号给PLC_4", "db9000.",1718.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_25", "db9000.",1719.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_26", "db9000.",1720.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_27", "db9000.",1721.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_43", "db9000.",1722.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_28", "db9000.",1723.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_44", "db9000.",1724.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_45", "db9000.",1725.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_29", "db9000.",1726.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_46", "db9000.",1727.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_47", "db9000.",1728.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_48", "db9000.",1729.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_49", "db9000.",1730.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_50", "db9000.",1731.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_51", "db9000.",1732.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_52", "db9000.",1733.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_53", "db9000.",1734.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_54", "db9000.",1735.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_55", "db9000.",1736.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_56", "db9000.",1737.00, "byte", 1);
            op42.addItem("MES下发程序号给PLC_57", "db9000.",1738.00, "byte", 1);
            stations.add(op42);

            //创建工位OP43
            Station op43 = new Station("OP43","OP43");
            op43.addItem("设备ID_16", "db9000.",1740.00, "string", 12);
            op43.addItem("工位 ID_16", "db9000.",1754.00, "string", 12);
            op43.addItem("工序零件身份证_16", "db9000.",1768.00, "string", 24);
            op43.addItem("PLC请求MES读码并绑定", "db9000.",1794.00, "byte", 1);
            op43.addItem("扫码绑定", "db9000.",1796.00, "string", 24);
            op43.addItem("MES读取并绑定完成_1", "db9000.",1822.00, "bool", 1);
            op43.addItem("MES读取并绑定完成_1", "db9000.",1822.00, "bool", 1);
            stations.add(op43);

            //创建工位OP44
            Station op44 = new Station("OP44","OP44");
            op44.addItem("设备ID_17", "db9000.",1824.00, "string", 12);
            op44.addItem("工位 ID_17", "db9000.",1838.00, "string", 12);
            op44.addItem("工序零件身份证_17", "db9000.",1852.00, "string", 24);
            op44.addItem("PLC请求MES读取产品码_2", "db9000.",1878.00, "byte", 1);
            op44.addItem("产品码_2", "db9000.",1880.00, "string", 24);
            op44.addItem("MES读取完成_42", "db9000.",1906.00, "bool", 1);
            op44.addItem("PLC请求MES读取拧紧螺丝顺序号_2", "db9000.",1907.00, "byte", 1);
            op44.addItem("MES读取完成_43", "db9000.",1908.00, "bool", 1);
            op44.addItem("MES下发程序号给PLC_2", "db9000.",1909.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_30", "db9000.",1910.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_31", "db9000.",1911.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_32", "db9000.",1912.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_33", "db9000.",1913.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_58", "db9000.",1914.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_59", "db9000.",1915.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_60", "db9000.",1916.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_61", "db9000.",1917.00, "byte", 1);
            op44.addItem("MES下发扭矩结果给PLC_62", "db9000.",1918.00, "byte", 1);
            op44.addItem("PLC请求MES读取全检结果", "db9000.",1919.00, "byte", 1);
            op44.addItem("视觉全检参数", "db9000.",1920.00, "byte", 1);
            op44.addItem("MES读取完成_44", "db9000.",1921.00, "bool", 1);
            stations.add(op44);
        } catch (StreamReadException e) {
            throw new RuntimeException(e);
        } catch (DatabindException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
        /**
     * 当工位复选框状态改变时调用此方法
     */
    /**
     * 工位复选框状态变化时的处理方法
     */
    private void onStationCheckBoxChanged() {
        // 更新选中的工位列表
        updateSelectedStations();
        
        // 更新全选复选框状态
        updateSelectAllCheckBoxState();
        
        // 更新工位数据网格
        updateStationDataGrid();
        
        // 只有在PLC已连接且有工位被选中的情况下才启用读取按钮
        stationReadButton.setDisable(!(s7PLC != null && s7PLC.checkConnected() && !selectedStations.isEmpty()));
        stationStopButton.setDisable(true);  // 确保停止按钮初始状态为禁用
        
        // 显示选中的工位数量
        if (!selectedStations.isEmpty()) {
            sendMsgToShow(" 已选择" + selectedStations.size() + "个工位");
        } else {
            sendMsgToShow(" 未选择任何工位");
        }
    }
    
    /**
     * 全选/取消全选按钮的动作处理
     */
    @FXML
    public void onSelectAllAction(ActionEvent event) {
        boolean selectAll = selectAllCheckBox.isSelected();
        
        // 设置所有工位复选框的选中状态
        for (CheckBox checkBox : stationListView.getItems()) {
            checkBox.setSelected(selectAll);
        }
        
        // 更新选中的工位列表
        updateSelectedStations();
        
        // 更新工位数据网格
        updateStationDataGrid();
        
        // 只有在PLC已连接且有工位被选中的情况下才启用读取按钮
        stationReadButton.setDisable(!(s7PLC != null && s7PLC.checkConnected() && !selectedStations.isEmpty()));
    }
    
    /**
     * 刷新工位列表按钮的动作处理
     */
    @FXML
    public void refreshStationsAction(ActionEvent event) {
        // 重新初始化工位列表
        initializeStationListView();
        
        // 清空选中的工位列表
        selectedStations.clear();
        
        // 更新工位数据网格
        updateStationDataGrid();
        
        // 禁用读取按钮
        stationReadButton.setDisable(true);
    }
    
    /**
     * 更新选中的工位列表
     */
    private void updateSelectedStations() {
        selectedStations.clear();
        
        for (CheckBox checkBox : stationListView.getItems()) {
            if (checkBox.isSelected()) {
                Station station = (Station) checkBox.getUserData();
                selectedStations.add(station);
            }
        }
        
        // 按工位名称排序
        selectedStations.sort((s1, s2) -> s1.getName().compareTo(s2.getName()));
    }
    
    /**
     * 更新全选复选框的状态
     */
    private void updateSelectAllCheckBoxState() {
        int totalCount = stationListView.getItems().size();
        int selectedCount = 0;
        
        for (CheckBox checkBox : stationListView.getItems()) {
            if (checkBox.isSelected()) {
                selectedCount++;
            }
        }
        
        // 如果所有工位都被选中，则全选复选框为选中状态
        // 如果部分工位被选中，则全选复选框为不确定状态
        // 如果没有工位被选中，则全选复选框为未选中状态
        if (selectedCount == totalCount) {
            selectAllCheckBox.setSelected(true);
            selectAllCheckBox.setIndeterminate(false);
        } else if (selectedCount > 0) {
            selectAllCheckBox.setSelected(true);
            selectAllCheckBox.setIndeterminate(true);
        } else {
            selectAllCheckBox.setSelected(false);
            selectAllCheckBox.setIndeterminate(false);
        }
    }

        private void updateStationDataGrid () {
            // 清空网格内容(保留表头)
            stationDataGrid.getChildren().removeIf(node ->
                    GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) > 0
            );

            // 如果没有选中的工位，则不显示任何数据
            if (selectedStations.isEmpty()) {
                return;
            }

            // 添加所有选中工位的检测项数据
            int rowIndex = 1;
            
            // 遍历所有选中的工位（已按名称排序）
            for (Station station : selectedStations) {
                List<StationItem> items = station.getItems();
                
                // 添加工位分隔行
                Label separatorLabel = new Label("===== " + station.getName() + " =====");
                separatorLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
                GridPane.setColumnSpan(separatorLabel, 6); // 跨越所有列
                stationDataGrid.add(separatorLabel, 0, rowIndex++);
                
                // 添加该工位的所有检测项
                for (StationItem item : items) {
                    stationDataGrid.add(new Label(station.getName()), 0, rowIndex);
                    stationDataGrid.add(new Label(item.getName()), 1, rowIndex);
                    stationDataGrid.add(new Label(item.getDb()), 2, rowIndex);
                    stationDataGrid.add(new Label(String.format("%.2f", item.getOffset())), 3, rowIndex);
                    stationDataGrid.add(new Label(item.getDataType()), 4, rowIndex);
                    TextArea resultLabel = new TextArea("");
                    stationDataGrid.add(resultLabel, 5, rowIndex);
                    item.setResultLabel(resultLabel); // 添加到StationItem中以便更新
                    
                    rowIndex++;
                }
            }
        }

        /**
         * 停止所有正在运行的工位线程
         */
        private void stopAllStationThreads() {
            log.info("停止所有工位线程开始, 当前线程数: {}", stationThreads.size());
            
            // 遍历所有工位线程并停止它们
            for (Map.Entry<Station, StationDataReadThread> entry : stationThreads.entrySet()) {
                StationDataReadThread thread = entry.getValue();
                Station station = entry.getKey();
                
                if (thread != null) {
                    // 将isRunning标志设置为false，通知线程停止运行
                    thread.isRunning = false;
                    
                    try {
                        // 给线程一些时间来完成当前操作并退出
                        thread.join(1000); // 等待最多1秒
                        
                        // 如果线程仍在运行，则尝试更强制的方法中断它
                        if (thread.isAlive()) {
                            log.warn("工位[{}]线程未能正常停止，尝试中断", station.getName());
                            thread.interrupt();
                        }
                        
                        log.info("工位[{}]线程已停止", station.getName());
                    } catch (InterruptedException e) {
                        log.error("停止工位[{}]线程时发生异常: {}", station.getName(), e.getMessage(), e);
                    }
                }
            }
            
            // 清空线程映射
            stationThreads.clear();
            log.info("所有工位线程已停止并清理完毕");
            
            // 通知GC回收资源
            System.gc();
        }

        @FXML
        public void stationReadButtonClickEvent() {
            log.info("开始工位数据读取...");
            
            if (StringUtils.isNotBlank(stationReadTime.getText())) {
                if (StringUtils.isNumeric(stationReadTime.getText())) {
                    if (s7PLC != null && s7PLC.checkConnected()) {
                        if (selectedStations.isEmpty()) {
                            sendMsgToShow(" 请选择至少一个工位！");
                            return;
                        }
                        
                        // 先停止所有正在运行的线程以释放资源
                        stopAllStationThreads();
                        
                        // 为每个选中的工位创建并启动一个线程
                        int interval = Integer.parseInt(stationReadTime.getText());
                        String timeUnit = stationTimeChoiceBox.getValue();
                        
                        log.info("准备为{}个工位启动数据读取线程，读取间隔: {}{}", selectedStations.size(), interval, timeUnit);
                        
                        for (Station station : selectedStations) {
                            try {
                                StationDataReadThread thread = new StationDataReadThread(
                                        s7PLC,
                                        station,
                                        timeUnit,
                                        interval
                                );
                                thread.start();
                                stationThreads.put(station, thread);
                                log.info("工位[{}]数据读取线程已启动", station.getName());
                            } catch (Exception e) {
                                log.error("启动工位[{}]数据读取线程失败: {}", station.getName(), e.getMessage(), e);
                            }
                        }
                        
                        stationReadButton.setDisable(true);
                        stationStopButton.setDisable(false);
                        sendMsgToShow(" 已启动" + selectedStations.size() + "个工位的数据读取线程");
                    } else {
                        sendMsgToShow(" PLC未连接！");
                    }
                } else {
                    sendMsgToShow(" 读取频率格式错误！");
                }
            } else {
                sendMsgToShow(" 读取频率不能为空！");
            }
        }

        @FXML
        public void stationStopButtonClickEvent() {
            log.info("停止工位数据读取...");
            
            // 停止所有正在运行的工位线程
            stopAllStationThreads();
            
            // 更新按钮状态
            stationReadButton.setDisable(!(s7PLC != null && s7PLC.checkConnected() && !selectedStations.isEmpty()));
            stationStopButton.setDisable(true);
            
            sendMsgToShow(" 已停止所有工位数据读取线程");
        }

        /**
         * 连接按钮的点击事件
         */
        public void connectButtonActionEvent () {
            try {
                sendMsgToShow("");
                String ipAddressText = ipAddress.getText();
                String httpPort = port.getText();
                // 获取选中的PLC账号
                String selectedAccount = plcAccountComboBox.getValue();
                if (selectedAccount != null && !selectedAccount.isEmpty()) {
                    String plcId = selectedAccount.split(" ")[0];
                    ApiService.setCurrentPlcId(plcId);
                    log.info("已选择PLC账号: {}", plcId);
                } else {
                    log.warn("未选择PLC账号，将使用默认账号");
                }
                if (StringUtils.isNotBlank(httpPort)){
                    //端口不为空的话
                    TighteningController instance = TighteningController.getInstance(Integer.parseInt(httpPort));
                    instance.startServer();
                }
                if (StringUtils.isNotBlank(ipAddressText)) {
                    if (IpAddressUtil.isIP(ipAddressText)) {
                        // 获取模型下拉框选中的值
                        String modelText = modelComboBox.getValue();
                        switch (modelText) {
                            case "200":
                                s7PLC = new S7PLC(EPlcType.S200, ipAddressText);
                                break;
                            case "200SMART":
                                s7PLC = new S7PLC(EPlcType.S200_SMART, ipAddressText);
                                break;
                            case "300":
                                s7PLC = new S7PLC(EPlcType.S300, ipAddressText);
                                break;
                            case "400":
                                s7PLC = new S7PLC(EPlcType.S400, ipAddressText);
                                break;
                            case "1200":
                                s7PLC = new S7PLC(EPlcType.S1200, ipAddressText);
                                break;
                            case "1500":
                                s7PLC = new S7PLC(EPlcType.S1500, ipAddressText);
                                break;
                            default:
                                break;
                        }
                        // 设置超时时间
                        s7PLC.setConnectTimeout(3000);
                        s7PLC.setReceiveTimeout(5000);
                        s7PLC.connect();
                        if (s7PLC.checkConnected()) {
                            connectButton.setDisable(true);
                            disconnectButton.setDisable(false);
                            readButton.setDisable(false);
                            batchReadButton.setDisable(false);

                            // 如果已经选择了工位，启用工位读取按钮
                            if (!selectedStations.isEmpty()) {
                                stationReadButton.setDisable(false);
                            }

                            statusImg.setImage(new Image("/images/greenRound.png"));
                            sendMsgToShow("PLC  " + ipAddressText + " 连接成功！");
                        } else {
                            sendMsgToShow(" PLC连接失败！");
                        }
                    } else {
                        sendMsgToShow(" Ip地址格式错误！");
                    }
                } else {
                    sendMsgToShow(" Ip地址不能为空！");
                }
            } catch (Exception e) {
                sendMsgToShow(" PLC连接失败！");
            }
        }


        /**
         * @author JayNH
         * @description //TODO 断开连接点击事件
         * @date 2023/2/24 15:23
         */
        public void disconnectionButtonActionEvent () {
            try {
                sendMsgToShow("");
                if (s7PLC != null) {
                    log.info("开始断开PLC连接...");
                    
                    // 先停止所有工位数据读取线程
                    stopAllStationThreads();
                    
                    // 停止数据读取线程
                    if (dataReadThread != null) {
                        log.info("停止数据读取线程...");
                        dataReadThread.isRunning = false;
                        try {
                            dataReadThread.join(1000); // 等待最多1秒
                            if (dataReadThread.isAlive()) {
                                dataReadThread.interrupt();
                            }
                        } catch (InterruptedException e) {
                            log.error("等待数据读取线程结束时发生异常: {}", e.getMessage(), e);
                        }
                        dataReadThread = null;
                    }
                    
                    // 关闭PLC连接
                    s7PLC.close();
                    log.info("PLC连接已关闭");
                    
                    // 更新UI状态
                    connectButton.setDisable(false);
                    disconnectButton.setDisable(true);
                    readButton.setDisable(true);
                    stopButton.setDisable(true);
                    batchReadButton.setDisable(true);
                    startCurveShader.setDisable(true);
                    stopCurveShader.setDisable(true);
                    stationReadButton.setDisable(true);
                    stationStopButton.setDisable(true);

                    connectButton.requestFocus();
                    statusImg.setImage(new Image("/images/redRound.png"));
                    sendMsgToShow(" PLC连接已断开");
                    
                    // 建议进行垃圾回收
                    System.gc();
                }
            } catch (Exception e) {
                log.error("断开PLC连接时发生异常: {}", e.getMessage(), e);
                tipsLabel.setText(sdf.format(new Date()) + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * @param actionEvent 事件
         * @author JayNH
         * @description //TODO 根据类型按钮选择对应的数据类型并填充到输入框中
         * @date 2023/2/24 16:09
         */
        public void dataTypeButtonChooseActionEvent (ActionEvent actionEvent){
            Button button = (Button) actionEvent.getSource();
            String typeName = button.getId();
            dataType.setText(typeName);
        }

        /**
         * @author JayNH
         * @description //TODO 开始读取数据按钮点击事件
         * @date 2023/2/24 16:23
         */
        public void readButtonClickEvent () {
            sendMsgToShow("");
            // 判断地址是否为空
            if (StringUtils.isNotBlank(readOffset.getText())) {
                // 判断数据类型是否有选择
                if (StringUtils.isNotBlank(dataType.getText())) {
                    // 判断读取间隔时间
                    if (StringUtils.isNotBlank(readTime.getText())) {
                        if (StringUtils.isNumeric(readTime.getText())) {
                            if (s7PLC != null && s7PLC.checkConnected()) {
                                // 判断地址是否为空以及地址填写的是否正确
                                String offsetString = readOffset.getText();
                                int strLength = 0;
                                if (StringUtils.isNotBlank(stringLength.getText())) {
                                    strLength = Integer.parseInt(stringLength.getText());
                                }
                                dataReadThread = new DataReadThread(s7PLC, dataType.getText(), offsetString, strLength, timeChoiceBox.getValue(), Integer.parseInt(readTime.getText()), readResult, series);
                                dataReadThread.start();
                                readButton.setDisable(true);
                                stopButton.setDisable(false);
                                startCurveShader.setDisable(false);
                                plcAccountComboBox.setDisable(true);
                            } else {
                                sendMsgToShow(" PLC未连接！");
                            }
                        } else {
                            sendMsgToShow(" 读取频率格式错误！");
                        }
                    } else {
                        sendMsgToShow(" 读取频率不能为空！");
                    }
                } else {
                    sendMsgToShow(" 数据类型未选择！");
                }
            } else {
                sendMsgToShow(" 地址不能为空！");
            }
        }

        /**
         * @author JayNH
         * @description //TODO 停止读取数据按钮点击事件
         * @date 2023/2/24 16:24
         */
        public void stopButtonClickEvent() {
            if (dataReadThread != null) {
                try {
                    log.info("开始停止数据读取线程...");
                    
                    // 停止读取数据线程
                    dataReadThread.isRunning = false;
                    dataReadThread.isStartCurveShader = false;
                    
                    // 给线程一些时间来完成当前操作并退出
                    try {
                        dataReadThread.join(1000); // 等待最多1秒
                        
                        // 如果线程仍在运行，则尝试更强制的方法中断它
                        if (dataReadThread.isAlive()) {
                            log.warn("数据读取线程未能正常停止，尝试中断");
                            dataReadThread.interrupt();
                        }
                        
                        log.info("数据读取线程已停止");
                    } catch (InterruptedException e) {
                        log.error("等待数据读取线程结束时发生异常: {}", e.getMessage(), e);
                    }
                    
                    // 等线程停止后再释放引用
                    dataReadThread = null;
                    
                    // 更新按钮状态
                    readButton.setDisable(false);
                    stopButton.setDisable(true);
                    startCurveShader.setDisable(true);
                    stopCurveShader.setDisable(true);
                    
                    log.info("数据读取停止操作完成");
                    
                    // 建议进行垃圾回收
                    System.gc();
                } catch (Exception e) {
                    log.error("停止数据读取线程时发生异常: {}", e.getMessage(), e);
                    e.printStackTrace();
                    readButton.setDisable(true);
                    stopButton.setDisable(false);
                }
            }
        }

        /**
         * @author JayNH
         * @description //TODO 开启曲线数据渲染按钮点击事件
         * @date 2023/2/26 21:21
         */
        public void startCurveShaderClickEvent () {
            // 判断数据类型是否支持曲线渲染
            if (!dataType.getText().equalsIgnoreCase(FinalConstant.BOOL) && !dataType.getText().equalsIgnoreCase(FinalConstant.STRING)) {
                dataReadThread.isStartCurveShader = true;
                startCurveShader.setDisable(true);
                stopCurveShader.setDisable(false);
            } else {
                sendMsgToShow("该数据类型" + dataType.getText() + "不支持图表渲染！");
            }
        }

        /**
         * @author JayNH
         * @description //TODO 关闭曲线数据渲染按钮点击事件
         * @date 2023/2/26 21:21
         */
        public void stopCurveShaderClickEvent () {
            dataReadThread.isStartCurveShader = false;
            startCurveShader.setDisable(false);
            stopCurveShader.setDisable(true);
        }

        /**
         * @author JayNH
         * @description //TODO 批量读取数据按钮点击事件
         * @date 2023/2/27 15:41
         */
        public void batchReadDataClickEvent () {
            sendMsgToShow("");
            if (StringUtils.isNotBlank(batchReadOffset.getText())) {
                if (StringUtils.isNotBlank(batchReadLength.getText())) {
                    if (StringUtils.isNumeric(batchReadLength.getText())) {
                        if (s7PLC != null && s7PLC.checkConnected()) {
                            int length = Integer.parseInt(batchReadLength.getText());
                            byte[] data = s7PLC.readByte(batchReadOffset.getText(), length);
                            String byteString = FinalFunction.toHexString(data);
                            batchReadResult.setText(byteString);
                        } else {
                            sendMsgToShow(" PLC连接失败！");
                        }
                    } else {
                        sendMsgToShow(" 批量读取长度格式错误！");
                    }
                } else {
                    sendMsgToShow(" 批量读取长度不能为空！");
                }
            } else {
                sendMsgToShow(" 批量读取地址不能为空！");
            }
        }

        /**
         * @param actionEvent 事件
         * @author JayNH
         * @description //TODO 数据类型写入PLC
         * @date 2023/2/27 17:04
         */
        public void dataTypeWriteClickEvent (ActionEvent actionEvent){
            sendMsgToShow("");
            try {
                Button button = (Button) actionEvent.getSource();
                String dataTypeStr = button.getId();
                String dataType = dataTypeStr.split("_")[1];
                String offset = writeReadOffset.getText();
                String value = writeDataValue.getText();
                if (StringUtils.isNotBlank(offset)) {
                    if (StringUtils.isNotBlank(value)) {
                        if (s7PLC != null && s7PLC.checkConnected()) {
                            switch (dataType.toLowerCase()) {
                                case "bool":
                                    boolean boolVal = "true".equals(value) || "1".equals(value);
                                    s7PLC.writeBoolean(offset, boolVal);
                                    break;
                                case "byte":
                                    byte byteVal = Byte.parseByte(value);
                                    s7PLC.writeByte(offset, byteVal);
                                    break;
                                case "short":
                                    short shortVal = Short.parseShort(value);
                                    s7PLC.writeInt16(offset, shortVal);
                                    break;
                                case "ushort":
                                    int uShortVal = Integer.parseInt(value);
                                    s7PLC.writeUInt16(offset, uShortVal);
                                    break;
                                case "int":
                                case "long":
                                    int intVal = Integer.parseInt(value);
                                    s7PLC.writeInt32(offset, intVal);
                                    break;
                                case "uint":
                                case "ulong":
                                    long uintVal = Long.parseLong(value);
                                    s7PLC.writeUInt32(offset, uintVal);
                                    break;
                                case "float":
                                    float floatVal = Float.parseFloat(value);
                                    s7PLC.writeFloat32(offset, floatVal);
                                    break;
                                case "double":
                                    double doubleVal = Double.parseDouble(value);
                                    s7PLC.writeFloat64(offset, doubleVal);
                                    break;
                                default:
                                    break;
                            }
                        } else {
                            sendMsgToShow(" PLC连接失败！");
                        }
                    } else {
                        sendMsgToShow(" 写入数值不能为空！");
                    }
                } else {
                    sendMsgToShow(" 写入地址不能为空！");
                }
            } catch (Exception e) {
                sendMsgToShow(" 数据写入失败！");
                e.printStackTrace();
            }
        }

        /**
         * 将错误内容进行显示
         *
         * @param content 错误信息
         */
        private void sendMsgToShow (String content){
            tipsLabel.setText(sdf.format(new Date()) + " --> " + content);
        }

        @FXML
        private void openStationManager () {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/StationDataEditor.fxml"));
                Stage stage = new Stage();
                stage.setTitle("工位管理");
                stage.setScene(new Scene(loader.load()));

                // 获取工位编辑器控制器
                StationDataEditorController controller = loader.getController();
                // 传递当前工位数据
                controller.setStations(FXCollections.observableArrayList(stations));

                // 显示窗口
                stage.showAndWait();

                // 更新工位数据
                stations = controller.getStations();
            } catch (Exception e) {
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("错误");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        }
    private void initializePlcAccountComboBox() {
        try {
            // 获取所有PLC账号
            List<PlcAccount> accounts = ApiService.getAllPlcAccounts();

            // 创建下拉框项
            List<String> accountItems = new ArrayList<>();
            for (PlcAccount account : accounts) {
                accountItems.add(account.getPlcId() + " (" + account.getUsername() + ")");
            }

            // 设置下拉框项
            plcAccountComboBox.setItems(FXCollections.observableArrayList(accountItems));

            // 选择默认账号
            for (int i = 0; i < accounts.size(); i++) {
                if (accounts.get(i).isDefault()) {
                    plcAccountComboBox.getSelectionModel().select(i);
                    break;
                }
            }

            // 如果没有选中任何账号，则选择第一个
            if (plcAccountComboBox.getSelectionModel().getSelectedIndex() < 0 && !accounts.isEmpty()) {
                plcAccountComboBox.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            log.error("初始化PLC账号下拉框出错: {}", e.getMessage());
        }
    }

}
