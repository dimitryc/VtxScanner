import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;

class SerialReader implements Runnable {

    private BufferedReader br;
    private BlockingQueue<String> queue;

    SerialReader(InputStream in, BlockingQueue<String> queue) {
        this.br = new BufferedReader(new InputStreamReader(in));
        this.queue = queue;
    }

    private int freq;
    private int rssi;

    private boolean parseMessage(String message) {
        String[] array = message.split(" ");
        if (array.length == 2) {
            freq = Integer.parseInt(array[0]);
            rssi = Integer.parseInt(array[1]);
            return true;
        } else {
            System.out.println("ignore invalid data {"+message+"}");
        }
        return false;
    }

    private boolean readMessage() throws IOException {
        String line = br.readLine();
        if (VtxScanner.isVerbose()) {
            System.out.println("[SerialReader] received {" + line + "}");
        }

        if (parseMessage(line)) {
            if (!queue.remove(Integer.toString(freq))) {
                System.out.println("freq not in queue " + freq);
            }
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (readMessage()) {
                    VtxScanner.updateChart(freq, rssi);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
