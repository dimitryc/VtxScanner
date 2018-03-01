import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;

class SerialWriter implements Runnable {

    private OutputStream out;
    private BlockingQueue<String> queue;

    private volatile boolean isStopped = true;

    private int currentFreq = VtxScanner.MIN_FREQ;

    SerialWriter(OutputStream out, BlockingQueue<String> queue) {
        this.out = out;
        this.queue = queue;
    }

    @Override
    public void run() {
        while (true) {
            while (isStopped) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            sendMessage();
            currentFreq += 2;
            if (currentFreq > VtxScanner.MAX_FREQ) {
                currentFreq = VtxScanner.MIN_FREQ;
            }
        }
    }

    private void sendMessage() {
        String message = Integer.toString(currentFreq);
        try {
            queue.put(Integer.toString(currentFreq));
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

    public void stop() {
        isStopped = true;
    }

    public void resume() {
        isStopped = false;
    }

    void setCurrentFreq(int freq) {
        if (freq < VtxScanner.MIN_FREQ || freq > VtxScanner.MAX_FREQ) {
            System.out.println("invalid freq value "+freq);
            return;
        }

        if (isStopped) {
            this.currentFreq = freq;
            sendMessage();
        }
    }
}
