package com.znh.siemens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.znh.siemens.model.Station;
import com.znh.siemens.model.StationItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class StationDataEditorController implements Initializable {
    @FXML
    private TextField stationProcessField;
    @FXML
    private TextField stationNameField;

    @FXML
    private ListView<Station> stationListView;
    @FXML
    private TextField itemNameField;
    @FXML
    private TextField itemDbField;
    @FXML
    private TextField offsetField;
    @FXML
    private ComboBox<String> dataTypeComboBox;
    @FXML
    private ComboBox<String> itemTypeComboBox;
    @FXML
    private TextField lengthField;
    @FXML
    private ComboBox<String> arrayTypeComboBox;
    @FXML
    private TextField arrayLengthField;
    @FXML
    private TableView<StationItem> itemTableView;
    @FXML
    private TableColumn<StationItem, String> nameColumn;
    @FXML
    private TableColumn<StationItem, String> dbColumn;
    @FXML
    private TableColumn<StationItem, Double> offsetColumn;
    @FXML
    private TableColumn<StationItem, String> dataTypeColumn;
    @FXML
    private TableColumn<StationItem, String> itemTypeColumn;
    @FXML
    private TableColumn<StationItem, Integer> lengthColumn;

    private ObservableList<Station> stations = FXCollections.observableArrayList();
    private Station currentStation;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化常规数据类型下拉框
        dataTypeComboBox.setItems(FXCollections.observableArrayList(
            "bool", "byte", "short", "int", "float", "string"
        ));
        
        // 初始化数组类型下拉框
        arrayTypeComboBox.setItems(FXCollections.observableArrayList(
            "real[]", "bool[]"
        ));
        
        // 设置数组类型选择监听器
        arrayTypeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // 如果选择了数组类型，清空常规类型选择
                dataTypeComboBox.setValue(null);
                // 启用数组长度字段
                arrayLengthField.setDisable(false);
            }
        });
        
        // 设置常规类型选择监听器
        dataTypeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // 如果选择了常规类型，清空数组类型选择
                arrayTypeComboBox.setValue(null);
                // 对于常规类型，数组长度字段不需要
                arrayLengthField.setDisable(true);
            }
        });
        
        // 初始化检测项类别下拉框
        itemTypeComboBox.setItems(FXCollections.observableArrayList(
            "监听项", "读取项", "写入项", "普通项","螺钉号项","SN项","心跳项", "Code项","函数项_x","函数项_y"
        ));
        itemTypeComboBox.setValue("普通项"); // 设置默认值

        // 初始化表格列
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        dbColumn.setCellValueFactory(new PropertyValueFactory<>("db"));
        offsetColumn.setCellValueFactory(new PropertyValueFactory<>("offset"));
        itemTypeColumn.setCellValueFactory(new PropertyValueFactory<>("itemType"));
        dataTypeColumn.setCellValueFactory(new PropertyValueFactory<>("dataType"));
        lengthColumn.setCellValueFactory(new PropertyValueFactory<>("length"));

        // 设置工位列表选择监听器
        stationListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentStation = newVal;
                itemTableView.setItems(FXCollections.observableArrayList(currentStation.getItems()));
            }
        });
        
        // 设置检测项表格选择监听器
        itemTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                fillItemFields(newVal);
            }
        });

        // 设置工位列表数据
        stationListView.setItems(stations);
    }

    @FXML
    private void importButtonClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择工位数据文件");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON文件", "*.json")
        );
        
        // 设置初始目录为data文件夹
        File dataDir = new File("data");
        if (dataDir.exists()) {
            fileChooser.setInitialDirectory(dataDir);
        }
        
        File file = fileChooser.showOpenDialog(stationNameField.getScene().getWindow());
        if (file != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<Station> loadedStations = mapper.readValue(file, mapper.getTypeFactory().constructCollectionType(List.class, Station.class));
                
                if (loadedStations == null || loadedStations.isEmpty()) {
                    showAlert("导入的文件不包含有效的工位数据");
                    return;
                }
                
                stations.clear();
                stations.addAll(loadedStations);
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("提示");
                alert.setHeaderText(null);
                alert.setContentText(String.format("工位数据导入成功！\n共导入%d个工位", loadedStations.size()));
                alert.showAndWait();
            } catch (IOException e) {
                showAlert("导入数据失败：" + e.getMessage());
                e.printStackTrace(); // 添加详细日志输出
            } catch (Exception e) {
                showAlert("导入数据时发生未知错误：" + e.getMessage());
                e.printStackTrace(); // 添加详细日志输出
            }
        }
    }

    @FXML
    private void addStationButtonClick() {
        String stationName = stationNameField.getText().trim();
        String stationProcess = stationProcessField.getText().trim();
        if (!stationName.isEmpty()&& !stationProcess.isEmpty()) {
            Station station = new Station(stationName,stationProcess);
            stations.add(station);
            stationNameField.clear();
            stationProcessField.clear();
        }
    }

    @FXML
    private void editStationButtonClick() {
        Station selectedStation = stationListView.getSelectionModel().getSelectedItem();
        if (selectedStation == null) {
            showAlert("请选择要编辑的工位");
            return;
        }
        stationNameField.setText(selectedStation.getName());
    }

    @FXML
    private void deleteStationButtonClick() {
        Station selectedStation = stationListView.getSelectionModel().getSelectedItem();
        if (selectedStation == null) {
            showAlert("请选择要删除的工位");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("确认删除");
        confirmDialog.setHeaderText(null);
        confirmDialog.setContentText("确定要删除工位 " + selectedStation.getName() + " 吗？");

        if (confirmDialog.showAndWait().get() == ButtonType.OK) {
            stations.remove(selectedStation);
            currentStation = null;
            itemTableView.setItems(null);
        }
    }

    @FXML
    private Button addItemButton;
    
    /**
     * 获取表单数据并返回数据类型
     * @return 数据类型字符串，如果验证失败则返回null
     */
    private String getDataTypeFromForm() {
        String dataType = null;
        
        // 判断是常规类型还是数组类型
        if (dataTypeComboBox.getValue() != null) {
            dataType = dataTypeComboBox.getValue();
        } else if (arrayTypeComboBox.getValue() != null) {
            // 如果选择了数组类型，需要检查数组长度
            String arrayLengthStr = arrayLengthField.getText().trim();
            if (arrayLengthStr.isEmpty()) {
                showAlert("请输入数组长度");
                return null;
            }
            
            try {
                int arrayLength = Integer.parseInt(arrayLengthStr);
                // 构造数组类型字符串，例如：array[0..10] of real
                dataType = "array[0.." + (arrayLength) + "] of " + arrayTypeComboBox.getValue().replace("[]", "");
            } catch (NumberFormatException e) {
                showAlert("请输入有效的数组长度");
                return null;
            }
        }
        
        return dataType;
    }

    @FXML
    private void editItemButtonClick() {
        StationItem selectedItem = itemTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert("请选择要修改的检测项");
            return;
        }
        
        try {
            // 获取表单数据
            String name = itemNameField.getText().trim();
            String db = itemDbField.getText().trim();
            double offset = Double.parseDouble(offsetField.getText().trim());
            int length = Integer.parseInt(lengthField.getText().trim());
            String itemType = itemTypeComboBox.getValue();
            
            // 获取数据类型
            String dataType = getDataTypeFromForm();
            if (dataType == null) {
                return; // 数据类型验证失败
            }
            
            // 验证必填字段
            if (name.isEmpty() || db.isEmpty()) {
                showAlert("请填写完整信息");
                return;
            }
            
            // 更新选中的检测项
            selectedItem.setName(name);
            selectedItem.setDb(db);
            selectedItem.setOffset(offset);
            selectedItem.setDataType(dataType);
            selectedItem.setLength(length);
            selectedItem.setItemType(itemType);
            
            // 刷新表格显示
            itemTableView.refresh();
            
            // 清空表单字段
            clearItemFields();
            
            // 显示成功提示
            showSuccessAlert("检测项修改成功");
        } catch (NumberFormatException e) {
            showAlert("请输入有效的数字");
        }
    }
    
    @FXML
    private void addItemButtonClick() {
        // 正常的添加操作
        if (currentStation == null) {
            showAlert("请先选择工位");
            return;
        }

        try {
            String name = itemNameField.getText().trim();
            String db = itemDbField.getText().trim();
            double offset = Double.parseDouble(offsetField.getText().trim());
            int length = Integer.parseInt(lengthField.getText().trim());
            String itemType = itemTypeComboBox.getValue(); // 获取类别编号
            
            // 获取数据类型
            String dataType = getDataTypeFromForm();
            if (dataType == null) {
                return; // 数据类型验证失败
            }

            if (name.isEmpty() || db.isEmpty()) {
                showAlert("请填写完整信息");
                return;
            }

            StationItem item = new StationItem(name, db, offset, dataType, length);
            item.setItemType(itemType);
            currentStation.getItems().add(item);
            itemTableView.setItems(FXCollections.observableArrayList(currentStation.getItems()));
            clearItemFields();
            
            // 显示成功提示
            showSuccessAlert("检测项添加成功");
        } catch (NumberFormatException e) {
            showAlert("请输入有效的数字");
        }
    }

    @FXML
    private void deleteItemButtonClick() {
        StationItem selectedItem = itemTableView.getSelectionModel().getSelectedItem();
        if (selectedItem != null && currentStation != null) {
            currentStation.getItems().remove(selectedItem);
            itemTableView.setItems(FXCollections.observableArrayList(currentStation.getItems()));
        }
    }

    @FXML
    private void saveButtonClick() {
        try {
            // 确保data目录存在
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            // 创建JSON对象
            ObjectMapper mapper = new ObjectMapper();
            // 将stations列表转换为JSON字符串并保存到文件
            File file = new File(dataDir, "stations.json");
            List<Station> stationList = new ArrayList<>(stations);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, stationList);
            
            // 显示保存成功提示
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText("工位数据保存成功！\n保存路径：" + file.getAbsolutePath());
            alert.showAndWait();
            
            // 关闭窗口
            Stage stage = (Stage) stationNameField.getScene().getWindow();
            stage.close();
        } catch (IOException e) {
            showAlert("保存数据失败：" + e.getMessage());
        } catch (Exception e) {
            showAlert("保存数据时发生未知错误：" + e.getMessage());
        }
    }

    @FXML
    private void cancelButtonClick() {
        Stage stage = (Stage) stationNameField.getScene().getWindow();
        stage.close();
    }

    private void clearItemFields() {
        itemNameField.clear();
        itemDbField.clear();
        offsetField.clear();
        dataTypeComboBox.setValue(null);
        arrayTypeComboBox.setValue(null);
        arrayLengthField.clear();
        arrayLengthField.setDisable(true);
        lengthField.clear();
        itemTypeComboBox.setValue("普通项"); // 重置为默认值
    }
    
    private void fillItemFields(StationItem item) {
        // 清除当前字段
        clearItemFields();
        
        // 填充基本信息
        itemNameField.setText(item.getName());
        itemDbField.setText(item.getDb());
        offsetField.setText(String.valueOf(item.getOffset()));
        lengthField.setText(String.valueOf(item.getLength()));
        itemTypeComboBox.setValue(item.getItemType());
        
        // 处理数据类型
        String dataType = item.getDataType();
        if (dataType.startsWith("array")) {
            // 如果是数组类型，解析数组类型和长度
            // 例如：array[0..9] of real
            String arrayType = dataType.contains("real") ? "real[]" : "bool[]";
            arrayTypeComboBox.setValue(arrayType);
            
            // 解析数组长度
            try {
                int startIndex = dataType.indexOf("[") + 1;
                int endIndex = dataType.indexOf(".");
                int dotEndIndex = dataType.indexOf("]");
                String endValue = dataType.substring(endIndex + 2, dotEndIndex);
                int arrayLength = Integer.parseInt(endValue); // 因为是从0开始的索引
                
                arrayLengthField.setText(String.valueOf(arrayLength));
                arrayLengthField.setDisable(false);
            } catch (Exception e) {
                // 如果解析失败，设置一个默认值
                arrayLengthField.setText("10");
            }
        } else {
            // 如果是常规类型
            dataTypeComboBox.setValue(dataType);
            arrayLengthField.setDisable(true);
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("警告");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showSuccessAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setStations(ObservableList<Station> stations) {
        this.stations = stations;
        stationListView.setItems(this.stations);
    }

    public ObservableList<Station> getStations() {
        return stations;
    }
}