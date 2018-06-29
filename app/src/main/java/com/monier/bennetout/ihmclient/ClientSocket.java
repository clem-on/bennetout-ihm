package com.monier.bennetout.ihmclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;

public class ClientSocket implements Runnable {

    public static final int STATUS_CONNECTED        = 1;
    public static final int STATUS_NOT_CONNECTED    = 2;
    private static final int SOCK_TIMEOUT = 10000;

    private static final int SOCKET_PORT = 65001;
    private Socket mySocket = null;
    private boolean isRunning = false;

    private InputStream myInputStream;
    private OutputStream myOutputStream;

    public interface ClientSocketListener {
        void onPositionsReceivedFromServer(double flechePos, double levagePos, double portePos, double niveauX, double niveauY);

        void onSocketStatusUpdate(int status);
    }

    private static final ArrayList<ClientSocketListener> myListeners = new ArrayList<>();
    public static void addListener(ClientSocketListener listener) {
        myListeners.add(listener);
    }

    public static void removeListener(ClientSocketListener listener) {
        if (myListeners.contains(listener))
            myListeners.remove(listener);
    }

    private Thread myThread;
    public void connect(String ipAddr) throws IOException {
        mySocket = new Socket(ipAddr, SOCKET_PORT);
        mySocket.setSoTimeout(SOCK_TIMEOUT);
        myInputStream = mySocket.getInputStream();
        myOutputStream = mySocket.getOutputStream();

        isRunning = true;
        myThread = new Thread(this);
        myThread.start();
        for (ClientSocketListener listener:myListeners) {
            listener.onSocketStatusUpdate(STATUS_CONNECTED);
        }
    }

    public void deconnect() throws IOException {
        if (mySocket == null)
            return;

        mySocket.close();
        mySocket = null;
        myInputStream = null;
        myOutputStream = null;
        stopRxThread();
    }

    private void writeToSocket(String texte) throws IOException {
        myOutputStream.write(texte.getBytes());
        myOutputStream.flush();
    }

    private void stopRxThread() {
        isRunning = false;
        for (ClientSocketListener listener:myListeners) {
            listener.onSocketStatusUpdate(STATUS_NOT_CONNECTED);
        }
    }

    private int readNextByte() throws IOException {
        int value;
        value = myInputStream.read();
        if (value == -1) {
            stopRxThread();
        }
        return value;
    }

    private void fireData(String data) {

        String[] values = data.split("/");
        if (values.length < 6)
            return;

        if (values[1].isEmpty())
            return;
        if (values[2].isEmpty())
            return;
        if (values[3].isEmpty())
            return;
        if (values[4].isEmpty())
            return;
        if (values[5].isEmpty())
            return;

        try {
            double fleche = Double.valueOf(values[1]);
            double levage = Double.valueOf(values[2]);
            double porte = Double.valueOf(values[3]);
            double inclinoX = Double.valueOf(values[4]);
            double inclinoY = Double.valueOf(values[5]);
            for (ClientSocketListener listener:myListeners) {
                listener.onPositionsReceivedFromServer(fleche, levage, porte, inclinoX, inclinoY);
            }
        } catch (NumberFormatException nfe) {
            return;
        }
    }

    @Override
    public void run() {

        final int READ_STATE = 1;
        final int WRITE_STATE = 2;
        int state = WRITE_STATE;

        int value = 0;
        final int NB_SLASH_PROTOC = 6;
        int nbSlash = 0;

//        String sValue = "";
        byte[] data = new byte[64];
        int indexData = 0;

        while (isRunning) {
            switch (state) {
                case WRITE_STATE:
                    try {
                        writeToSocket("A");
                    } catch (IOException e) {
                        e.printStackTrace();
                        stopRxThread();
                    }
                    state = READ_STATE;
                    break;

                case READ_STATE:
                    try {
                        value = readNextByte();
                    }catch (SocketTimeoutException ste) {
                        state = WRITE_STATE;
                    }catch (IOException e) {
                        stopRxThread();
                        e.printStackTrace();
                    }

//                    sValue += (new String(new byte[]{(byte) value}));
                    data[indexData] = (byte) value;
                    indexData++;
                    if (value == '/') {
                        nbSlash++;
                    }
                    if (nbSlash >= NB_SLASH_PROTOC) {
                        nbSlash = 0;
                        fireData(new String(Arrays.copyOf(data, indexData)));
                        try {
                            writeToSocket(new String(Arrays.copyOf(data, indexData)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
//                        sValue = "";
                        data = new byte[64];
                        indexData = 0;
                        state = WRITE_STATE;
                    }

                    break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
