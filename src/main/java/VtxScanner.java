import gnu.io.NRSerialPort;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
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
                case "-p":
                    port = args[++i];
                    break;
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

        if (port == null) {
            System.err.println("Usage: java -jar VtxScanner.jar -p port");
            System.exit(1);
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

    public void start(Stage primaryStage) {
        List<String> args = getParameters().getRaw();
        parseArgs(args.toArray(new String[args.size()]));

        initSerial();

        initUI(primaryStage);
    }

    private SerialWriter writer;

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

        final ToggleButton stopButton = new ToggleButton("Scan");
        stopButton.setOnAction(event -> {
            if (stopButton.isSelected()) {
                stopButton.setText("Stop");
                writer.resume();
            } else {
                stopButton.setText("Scan");
                writer.stop();
            }
        });

        final TextField textField = new TextField();
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

        VBox vbox = new VBox();
        vbox.getChildren().addAll(lineChart, stopButton, textField);

        Scene scene = new Scene(vbox, 800, 450);
        scene.getStylesheets().add("style.css");

        primaryStage.setTitle("VtxScanner");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initSerial() {
        if (isVerbose()) {
            System.out.println("[SerialPort] port=" + port + ", baudRate=" + baudRate);
        }

        NRSerialPort serial = new NRSerialPort(port, baudRate);
        serial.connect();

        serial.getSerialPortInstance().disableReceiveTimeout();
        serial.getSerialPortInstance().enableReceiveThreshold(1);

        InputStream in = serial.getInputStream();
        OutputStream out = serial.getOutputStream();

        BlockingQueue<String> queue = new LinkedBlockingQueue<>(1);
        writer = new SerialWriter(out, queue);
        SerialReader reader = new SerialReader(in, queue);

        new Thread(writer).start();
        new Thread(reader).start();
    }

}

