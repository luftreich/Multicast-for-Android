package com.wade.multicast;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends Activity {
    EditText etPort;
    EditText etMsg;
    EditText etRecvMsg;
    MulticastSocket receiveSock = null;
    WifiManager.MulticastLock mLock = null;
    String recvMsg=null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);

        etPort = (EditText)findViewById(R.id.etPort);
        etMsg = (EditText)findViewById(R.id.etMsg);
        etRecvMsg = (EditText)findViewById(R.id.etRecvMsg);
        Button btSend = (Button)findViewById(R.id.btSend);
        btSend.setOnClickListener(
            new Button.OnClickListener() {
                public void onClick(View v) {
                    clickedTransmit();
                }
            }
        );

        WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        mLock = wifi.createMulticastLock("pseudo-ssdp");
        mLock.acquire();
        NetworkInterface nif = null;
        try {
            nif = NetworkInterface.getByName("wlan0");
        } catch (SocketException e) {
            e.printStackTrace();
        }
        Enumeration<InetAddress> enAddr = nif.getInetAddresses();
        InetAddress addr = null;
        while (enAddr.hasMoreElements()) {
            InetAddress x = enAddr.nextElement();
            System.out.println("@ " + x.toString());
            if (x instanceof Inet4Address) addr = x;
            else if (addr == null) addr = x;
        }
        try {
            receiveSock = new MulticastSocket(getPort());
            receiveSock.joinGroup(InetAddress.getByName("224.224.224.224"));
            receiveSock = new MulticastSocket(new InetSocketAddress(addr, getPort()));
            receiveSock.setNetworkInterface(nif);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Thread t = new Thread(new ReceiveTask(), "multicast listener");
        System.out.println("try to call ReceiveTask() start..."+addr.toString()+":"+getPort());
        t.start();
    }

    public int getPort() {
        return new Integer(etPort.getText().toString());
    }
    public String getMsg() {
        return etMsg.getText().toString();
    }
    public void clickedTransmit()
    {
        int port = getPort();
        final String msg = getMsg();

        byte[] bytes = null;
        try {
            bytes = getMsg().getBytes("UTF-8");
            if (null != bytes && null != receiveSock) {
                receiveSock.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("224.224.224.224"), port));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class ReceiveTask implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[4<<10];
            DatagramPacket pkt = null;
            pkt = new DatagramPacket(buffer, buffer.length);
            System.out.println("ReceiveTask BEGIN....");
            try {
                while (null != receiveSock) {
                    receiveSock.receive(pkt);
                    recvMsg = pkt.getAddress().getHostAddress() + ":" + pkt.getPort() + "\n" +
                        new String(pkt.getData(), 0, pkt.getLength(), "UTF-8") + "\n";
                    System.out.println(recvMsg);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            etRecvMsg.setText(etRecvMsg.getText()+recvMsg);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("ReceiveTask END");
        }
    }

    @Override
    protected void onDestroy() {
        receiveSock.close();
        mLock.release();
        super.onDestroy();
    }
}
