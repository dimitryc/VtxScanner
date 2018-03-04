import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

class SerialWriter extends Thread {

    private OutputStream out;
    private BlockingQueue<String> queue;

    private volatile boolean scan = false;
    private volatile boolean exit = false;

    private volatile int currentFreq =
            VtxScanner.MIN_FREQ + (VtxScanner.MAX_FREQ - VtxScanner.MIN_FREQ) / 2;

    SerialWriter(OutputStream out, BlockingQueue<String> queue) {
        this.out = out;
        this.queue = queue;
    }

    @Override
    public void run() {
        while (!exit) {
            sendMessage();
            if (scan) {
                incrementFrequency();
            }
        }

        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (VtxScanner.isVerbose()) {
            System.out.println("[SerialWriter] exiting");
        }
    }

    private synchronized void sendMessage() {
        String message = Integer.toString(currentFreq);
        try {
            if (!queue.offer(message, 1, TimeUnit.SECONDS)) {
                queue.clear();
                return;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (VtxScanner.isVerbose()) {
            System.out.println("[SerialWriter] send {" + message + "}");
        }

        try {
            this.out.write((currentFreq+"\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void scan(boolean scan) {
        this.scan = scan;
    }

    synchronized void setCurrentFreq(int freq) {
        if (freq < VtxScanner.MIN_FREQ || freq > VtxScanner.MAX_FREQ) {
            System.out.println("invalid freq value "+freq);
            return;
        }

        this.currentFreq = freq;
    }

    private synchronized void incrementFrequency() {
        currentFreq += 2;
        if (currentFreq > VtxScanner.MAX_FREQ) {
            currentFreq = VtxScanner.MIN_FREQ;
        }
    }

    private synchronized void decrementFrequency() {
        currentFreq -= 2;
        if (currentFreq < VtxScanner.MIN_FREQ) {
            currentFreq = VtxScanner.MAX_FREQ;
        }
    }

    void left() {
        decrementFrequency();
    }

    void right() {
        incrementFrequency();
    }

    void markStop() {
        exit = true;
    }
}
