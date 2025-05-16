package com.znh.siemens;

import com.znh.siemens.config.DemeterApiConfig;
import com.znh.siemens.config.DemeterApiConfigLoader;
import com.znh.siemens.controller.SiemensWindowsController;
import com.znh.siemens.controller.TighteningController;
import com.znh.siemens.utils.DemeterApiService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author JayNH
 * @Description TODO 启动类
 * @Date 2023-12-27 14:21
 * @Version 1.0
 */
@Slf4j
public class StartApplication extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try{
            FXMLLoader fxmlLoader = SiemensWindowsController.getFxmlLoader();
            Parent root = fxmlLoader.load();
            primaryStage.setTitle("西门子通讯程序 V1.0");
            primaryStage.getIcons().add(new Image(StartApplication.class.getResource("/images/icon.jpg").toExternalForm()));
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(800);
            // 允许调整窗口大小以适应不同分辨率
            primaryStage.setOnCloseRequest(this::exit);
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
            log.info("Program start!!!");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void exit(WindowEvent windowEvent){
        TighteningController.getInstance(0).stopServer();
        Platform.exit();
    }
}
