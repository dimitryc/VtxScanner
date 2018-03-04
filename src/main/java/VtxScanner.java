import gnu.io.NRSerialPort;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VtxScanner extends Application {

    private static String port;
    private static int baudRate = 115200;
    private static boolean verbose = false;

    static boolean isVerbose() {
        return verbose;
    }

    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-baudRate":
                    baudRate = Integer.parseInt(args[++i]);
                    break;
                case "-v":
                    verbose = true;
                    break;
                default:
                    System.err.println("Invalid param: " + args[i]);
                    System.exit(1);
            }
        }
    }

    private final static XYChart.Series<Number, Number>
            series1 = new XYChart.Series<>();
    private final static XYChart.Series<Number, Number>
            series2 = new XYChart.Series<>();

    private final static Map<Number, XYChart.Data<Number, Number>>
            freqToXYDataMap = new HashMap<>();

    private static boolean verticalLineCreated;
    private static XYChart.Data<Number, Number> verticalLineTop;
    private static XYChart.Data<Number, Number> verticalLineBottom = null;

    public static void updateChart(final int freq, final int rssi) {
        Platform.runLater(() -> {
            updateMainLine(freq, rssi);
            updateVerticalLine(freq, rssi);
            updateLabels(freq, rssi);
        });
    }

    private static void updateMainLine(int freq, int rssi) {
        XYChart.Data<Number, Number> xyData = freqToXYDataMap.get(freq);
        if (xyData == null) {
            xyData = new XYChart.Data<>(freq, rssi);
            series1.getData().add(xyData);
            freqToXYDataMap.put(freq, xyData);
        } else {
            xyData.setYValue(rssi);
        }
    }

    private static int maxRssi = 0;

    private static void updateVerticalLine(int freq, int rssi) {
        maxRssi = Math.max(maxRssi, rssi);
        if (!verticalLineCreated) {
            verticalLineCreated = true;
            verticalLineTop = new XYChart.Data<>(freq, maxRssi);
            verticalLineBottom = new XYChart.Data<>(freq, 0);

            series2.getData().add(verticalLineTop);
            series2.getData().add(verticalLineBottom);
        } else {
            verticalLineTop.setXValue(freq);
            verticalLineTop.setYValue(maxRssi);

            verticalLineBottom.setXValue(freq);
            verticalLineBottom.setYValue(0);
        }
    }

    private static int currentFreq = 0;

    private static void updateLabels(int freqValue, int rssiValue) {
        if (freqValue != currentFreq) {
            textField.setText(Integer.toString(freqValue));
            currentFreq = freqValue;
        }
        rssiLabel.setText("Rssi="+Integer.toString(rssiValue));
    }

    public void start(Stage primaryStage) {
        List<String> args = getParameters().getRaw();
        parseArgs(args.toArray(new String[args.size()]));

        initUI(primaryStage);
    }

    public static int MIN_FREQ = 5600;
    public static int MAX_FREQ = 6000;

    private void initUI(Stage primaryStage) {
        final NumberAxis xAxis = new NumberAxis(MIN_FREQ, MAX_FREQ, 100);
        final NumberAxis yAxis = new NumberAxis();

        final LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setAnimated(false);

        series1.setName("Frequency");
        series2.setName("Current");

        lineChart.getData().add(series1);
        lineChart.getData().add(series2);

        HBox controlBox = initControlBox();
        controlBox.setDisable(true);
        HBox connectBox = initConnectBox(controlBox);

        rssiLabel = new Label();

        VBox vbox = new VBox();
        vbox.getChildren().addAll(lineChart, connectBox, controlBox, rssiLabel);

        Scene scene = new Scene(vbox, 800, 450);
        scene.getStylesheets().add("style.css");

        primaryStage.setTitle("VtxScanner");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private static Label rssiLabel;
    private static TextField textField;

    private HBox initConnectBox(HBox controlBox) {
        HBox hbox = new HBox();
        final ToggleButton connectButton = new ToggleButton("Connect");
        connectButton.setOnAction(event -> {
            if (connectButton.isSelected()) {
                new Thread(this::connect).start();
                connectButton.setText("Disconnect");
                controlBox.setDisable(false);
            } else {
                new Thread(this::disconnect).start();
                connectButton.setText("Connect");
                controlBox.setDisable(true);
            }
        });

        final ComboBox<String> portsCompobox = new ComboBox<>();
        portsCompobox.getItems().addAll(NRSerialPort.getAvailableSerialPorts());
        portsCompobox.getSelectionModel().select(0);
        port = portsCompobox.getSelectionModel().getSelectedItem();

        portsCompobox.setOnAction(event ->
                port = portsCompobox.getSelectionModel().getSelectedItem());

        hbox.getChildren().addAll(connectButton, portsCompobox);

        return hbox;
    }

    private HBox initControlBox() {
        HBox hbox = new HBox();

        final ToggleButton scanButton = new ToggleButton("Scan");
        scanButton.setOnAction(event -> {
            if (scanButton.isSelected()) {
                writer.scan(true);
                scanButton.setText("Stop");
            } else {
                writer.scan(false);
                scanButton.setText("Scan");
            }
        });

        final Button leftButton = new Button("Left");
        leftButton.setOnAction(event -> writer.left());

        final Button rightButton = new Button("Right");
        rightButton.setOnAction(event -> writer.right());

        textField = new TextField();
        textField.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.ENTER)) {
                try {
                    writer.setCurrentFreq(Integer.parseInt(textField.getText()));
                } catch (NumberFormatException e) {
                    System.out.println("NumberFormatException "+e.getMessage());
                }
                textField.clear();
            }
        });

        hbox.getChildren().addAll(scanButton, leftButton, textField, rightButton);
        return hbox;
    }

    private NRSerialPort serial;
    private SerialReader reader;
    private SerialWriter writer;

    private void connect() {
        if (isVerbose()) {
            System.out.println("[SerialPort] connecting ... port=" + port + ", baudRate=" + baudRate);
        }

        serial = new NRSerialPort(port, baudRate);
        serial.connect();

        serial.getSerialPortInstance().disableReceiveTimeout();
        serial.getSerialPortInstance().enableReceiveThreshold(1);

        InputStream in = serial.getInputStream();
        OutputStream out = serial.getOutputStream();

        BlockingQueue<String> queue = new LinkedBlockingQueue<>(1);

        writer = new SerialWriter(out, queue);
        reader = new SerialReader(in, queue);

        reader.start();
        writer.start();

        if (isVerbose()) {
            System.out.println("[SerialPort] connected ");
        }
    }

    private void disconnect() {
        if (isVerbose()) {
            System.out.println("[SerialPort] disconnect ...");
        }

        reader.markStop();
        writer.markStop();

        try {
            reader.join();
            writer.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        serial.disconnect();
        serial = null;

        if (isVerbose()) {
            System.out.println("[SerialPort] disconnected ");
        }
    }

}

