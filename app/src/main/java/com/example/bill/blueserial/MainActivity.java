package com.example.bill.blueserial;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewSwitcher;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE;


public class MainActivity extends Activity {
    TextView out;
    EditText blueMacAdr;
    EditText gAdrEdTxt;
    CheckBox showRawCkBx;
    EditText editTextPollRate;

    public Sfi fi = new Sfi();


    private ViewSwitcher switcher;
    private static final int REFRESH_SCREEN = 1;

    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;
    private InputStream inStream = null;


    byte gAdr = 1;
    int polCnts = 0;
    final byte[] buf = new byte[256];
    int rxCnt = 0;
    int rxBytes = 0;
    boolean polBuzy = false;

    // Well known SPP UUID
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    //  private String address = "00:12:6F:00:7C:94";
    //  private String address = "00:12:6F:00:7C:1A";
    private String address = "20:14:11:05:00:40";

    int pollRate = 200;
    boolean polling = false;
    boolean showRaw = false;
    // Sfi.gageTypes gType = Sfi.gageTypes.ModBus696;
    Sfi.gageTypes gType = Sfi.gageTypes.G32;


    Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        switcher = (ViewSwitcher) findViewById(R.id.profileSwitcher);

        out = (TextView) findViewById(R.id.out);

        blueMacAdr = (EditText) findViewById(R.id.editBlueMac);
        blueMacAdr.setText(address);

        gAdrEdTxt = (EditText) findViewById(R.id.GAdrEditText);
        gAdrEdTxt.setText(String.format("%d", gAdr));

        editTextPollRate = (EditText) findViewById(R.id.editTextPollRate);
        editTextPollRate.setText(String.format("%d", pollRate));

        showRawCkBx = (CheckBox) findViewById(R.id.checkBox);
        showRawCkBx.setChecked(showRaw);

        Button button = (Button) findViewById(R.id.button1);
        Button set_DoneButton = (Button) findViewById(R.id.set_Done);

    final Button  OpenBt = (Button) findViewById(R.id.butOpenBt);
       final Button CloseBt = (Button) findViewById(R.id.butCloseBt);

        final Spinner spinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.protocols_array, android.R.layout.simple_spinner_item);
// Apply the adapter to the spinner
        spinner.setAdapter(adapter);
        spinner.setSelection(gType.type());

        set_DoneButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String s = gAdrEdTxt.getText().toString().trim();
                gAdr = (byte) Integer.parseInt(s);
                showRaw = showRawCkBx.isChecked();
                s = (String) spinner.getSelectedItem();
                gType = Sfi.gageTypes.getTypeS(s.trim());
                s = editTextPollRate.getText().toString().trim();
                pollRate = Integer.parseInt(s);
                if (pollRate < 15) pollRate = 15;
                s = blueMacAdr.getText().toString().trim().toUpperCase();
                if (!s.startsWith(address)) {
                    address = s;
                    if (outStream != null) {
                        switcher.showPrevious();
                        btClose();
                        btConnect();
                    }
                }
                switcher.showPrevious();  // Sswitches to the previous view
            }
        });


        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                polling = !polling;
                if (!polling) out.setText("Polling off");
                polCnts = 0;
            }
        });

        CloseBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                polling = false;
                btClose();
            }
        });
        OpenBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                btConnect();
                polCnts = 0;
            }
        });

        out.append("\n...In onCreate()...");

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        CheckBTState();

      //  btConnect();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if(outStream != null){
                    OpenBt.setEnabled( false);
                    CloseBt.setEnabled(true);
                } else{
                    OpenBt.setEnabled(true );
                    CloseBt.setEnabled(false);

                }
                if ((polling) && (!polBuzy)) poll();
                mHandler.postDelayed(this, pollRate);
            }
        };
        mHandler.post(runnable);
    }

    public void poll() {
        final int[] tic = new int[2];
        int lev = 0;
        int tmp = 0;
        String outS = "";
        //  out.setText("");
        polBuzy = true;
        try {
            for (int w = 0; w < 1; ++w) {
                if ((inStream == null) || (outStream == null)) return;
                switch (gType) {

                    case CCW:
                        buf[0] = (byte) 'A';
                        buf[1] = (byte) 'T';
                        if (outStream == null) return;
                        outStream.write(buf, 0, 2);
                        break;
                    case CW:
                    case G32:
                        while (inStream.available() > 0)
                            rxCnt = inStream.read(buf, 0, 1);
                        polCnts++;

                        buf[0] = (byte) (0x80 | gAdr);
                        buf[1] = (byte) 0x01;
                        if (outStream == null) return;

                        outStream.write(buf, 0, 2);
                        if (showRaw)
                            out.append(String.format("\nTx:[%02X][%02X]", buf[0], buf[1]));

                        for (tic[0] = 0; tic[0] < 10; ++tic[0]) {
                            if (inStream.available() > 1) break;
                            delay(15);
                        }

                        if (inStream.available() > 0) {
                            rxCnt = inStream.read(buf, 0, 2);
                            if (rxCnt == 2) {
                                lev = (((int) buf[0]) & 0xff) << 8;
                                lev |= ((int) buf[1]) & 0xff;
                                fi.tankData.lev = lev;
                                if (showRaw)
                                    out.append(String.format(" Rx:[%02X][%02X]", buf[0], buf[1]));

                                while (inStream.available() > 0)
                                    rxCnt = inStream.read(buf, 0, 1);
                                polCnts++;

                                buf[0] = (byte) (0x80 | gAdr);
                                buf[1] = (byte) 0x02;

                                outStream.write(buf, 0, 2);
                                if (showRaw)
                                    out.append(String.format("\nTx:[%02X][%02X]", buf[0], buf[1]));

                                for (tic[1] = 0; tic[1] < 10; ++tic[1]) {
                                    if (inStream.available() > 1) break;
                                    delay(15);
                                }

                                if (inStream.available() > 0) {
                                    rxCnt = inStream.read(buf, 0, 2);
                                    if (rxCnt == 2) {
                                        if (showRaw)
                                            out.append(String.format(" Rx:[%02X][%02X]", buf[0], buf[1]));

                                        tmp = (((int) buf[1]) & 0x1f) << 8;
                                        tmp |= ((int) buf[0]) & 0xff;
                                        if ((buf[1] & 0x20) == 0) tmp *= -1;
                                        fi.tankData.tmp = tmp * 2;
                                    }
                                }
                                outS = String.format("\n%5d Lev %6.3f, Tmp %5.1f, %d,%d",
                                        polCnts, (float) fi.tankData.lev / 384, ((float) fi.tankData.tmp) * 0.1, tic[0], tic[1]);
                            }
                        } else {
                            outS = String.format("\n No-Comm %5d  %d,%d", polCnts, tic[0], tic[1]);
                        }
                        break;
                    case ModBus696:
                        while (inStream.available() > 0)
                            rxCnt = inStream.read(buf, 0, 1);
                        polCnts++;

                        rxBytes = Sfi.makeModbusRequest(gAdr, (byte) 4, 0, 5, buf);
                        outStream.write(buf, 0, 8);
                        if (showRaw) {
                            out.append(String.format("\nTx "));
                            for (int x = 0; x < 8; ++x)
                                out.append(String.format("[%02X]", buf[x]));
                        }
                        delay(15);

                        for (tic[1] = 0; tic[1] < 20; ++tic[1]) {
                            if (inStream.available() >= rxBytes) break;
                            delay(15);
                        }

                        if (inStream.available() >= rxBytes) {
                            rxCnt = inStream.read(buf, 0, rxBytes);
                            if (rxCnt == rxBytes) {
                                if (showRaw) {
                                    out.append(String.format("\nRx "));
                                    for (int x = 0; x < rxBytes; ++x)
                                        out.append(String.format("[%02X]", buf[x]));
                                }

                                if (Sfi.verifyModbusMsg(gAdr, (byte) 4, rxBytes, buf)) {
                                    Sfi.parseModBus696(fi.tankData, buf);
                                    outS = String.format("\n%5d Lev %6.3f, Tmp %5.1f, Bsw %5.1f, %d,%d",
                                            polCnts, (float) fi.tankData.lev / 384, ((float) fi.tankData.tmp) * 0.1,
                                            (float) fi.tankData.bsw / 384, tic[0], tic[1]);
                                } else {
                                    outS = String.format("\n Bad-Comm %5d  %d,%d", polCnts, tic[0], tic[1]);
                                }
                            }
                        } else {
                            outS = String.format("\n No-Comm %5d  %d,%d", polCnts, tic[0], tic[1]);
                        }
                        break;
                }
                if (polCnts < 0) polCnts = 1;


                if (out.getLineCount() > 23) {
                    out.setText("");
                    out.performClick();
                }

                out.append(outS);
                outS = "";

            }
        } catch (IOException e) {
            polling = false;
            String msg = "In Poll() and an exception occurred during write: " + e.getMessage();
            msg = msg + ".\n\nCheck that the SPP UUID: " + MY_UUID.toString() + " exists on server.\n\n";
            out.append(msg);
            // AlertBox("Fatal Error", msg);
            btConnect();
            polBuzy = false;
            return;
        }
        polBuzy = false;
    }

    void delay(int mils) {
        try {

            Thread.sleep(mils);
        } catch (InterruptedException e) {
            // recommended because catching InterruptedException clears interrupt flag
            Thread.currentThread().interrupt();
            // you probably want to quit if the thread is interrupted
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            polling = false;
            switcher.showNext();  // Switches to the next view
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onResume() {
        super.onResume();
        out.append("\n...In onResume...");
       //   btConnect();
    }

    @Override
    public void onPause() {
        super.onPause();
        out.append("\n...In onPause()...");
        //  btClose();
    }

    @Override
    public void onStop() {
        super.onStop();
        out.append("\n...In onStop()...");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        out.append("\n...In onDestroy()...");
    }

    private void CheckBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on

        // Emulator doesn't support Bluetooth and will return null
        if (btAdapter == null) {
            AlertBox("Fatal Error", "Bluetooth Not supported. Aborting.");
        } else {
            if (btAdapter.isEnabled()) {
                out.append("\n...Bluetooth is enabled...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    public void AlertBox(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message + " Press OK to exit.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        // finish();
                    }
                }).show();
    }

    private void btClose() {

        polling = false;
        out.append("\n...In btClose()...");

        if (outStream != null) {
            try {
                outStream.flush();
            } catch (IOException e) {
                AlertBox("Fatal Error", "In btClose() and failed to flush output stream: " + e.getMessage() + ".");
            }
        }

        try {
            btSocket.close();
            outStream = null;
            inStream = null;

        } catch (IOException e2) {
            AlertBox("Fatal Error", "In btClose() and failed to close socket." + e2.getMessage() + ".");
        }
    }

    private void btConnect() {
        polling = false;
        out.append("\nbtConnect Attempting client connect...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            AlertBox("Fatal Error", "In btConnect() and socket create failed: " + e.getMessage() + ".");
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        btAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        try {
            btSocket.connect();
            out.append("\n...Connection established and data link opened...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                AlertBox("Fatal Error", "In btConnect() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        out.append("\n...Sending message to server...");

        try {
            outStream = btSocket.getOutputStream();
            inStream = btSocket.getInputStream();
            //buf.("AT+BAUD1\r\n");
            byte[] bytes = "AT\n\rAT+BAUD=1\r\n".getBytes();
            //     byte[] bytes = "AT+VERSION\r\n".getBytes();
         //    while(true) {

            outStream.write(bytes, 0, bytes.length - 1);
            for (int x = 0; x < bytes.length; ++x)
                out.append(String.format("[%02X]", bytes[x]));
            delay(150);

            if (inStream.available() >= 1) {
                rxCnt = inStream.read(buf, 0, inStream.available());


                out.append(String.format("\nRx "));
                for (int x = 0; x < rxCnt; ++x)
                    out.append(String.format("[%02X]", buf[x]));
            }

         //   }
        } catch (IOException e) {
            AlertBox("Fatal Error", "In btConnect() and output stream creation failed:" + e.getMessage() + ".");
        }
/*
    String message = "Hello from Android.\n";
    byte[] msgBuffer = message.getBytes();
    try {
   //  outStream.write(msgBuffer);
      // inStream.read(msgBuffer);
    } catch (IOException e) {
      String msg = "In onResume() and an exception occurred during write: " + e.getMessage();
      if (address.equals("00:00:00:00:00:00"))
        msg = msg + ".\n\nUpdate your server address from 00:00:00:00:00:00 to the correct address on line 37 in the java code";
      msg = msg + ".\n\nCheck that the SPP UUID: " + MY_UUID.toString() + " exists on server.\n\n";

      AlertBox("Fatal Error", msg);
    }*/

    }
}

