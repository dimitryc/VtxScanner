import java.io.*;
import java.util.concurrent.BlockingQueue;

class SerialReader extends Thread {

    private BufferedReader br;
    private BlockingQueue<String> queue;

    private volatile boolean exit = false;

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
        while (!exit) {
            try {
                if (readMessage()) {
                    VtxScanner.updateChart(freq, rssi);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (VtxScanner.isVerbose()) {
            System.out.println("[SerialReader] exiting");
        }
    }

    void markStop() {
        exit = true;
    }
}
