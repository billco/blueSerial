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
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewSwitcher;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE;

public class MainActivity extends Activity {
  TextView out;
  EditText blueMacAdr;
  EditText gAdrEdTxt;
  CheckBox showRawCkBx;


  private ViewSwitcher switcher;
  private static final int REFRESH_SCREEN = 1;

  private static final int REQUEST_ENABLE_BT = 1;
  private BluetoothAdapter btAdapter = null;
  private BluetoothSocket btSocket = null;
  private OutputStream outStream = null;
  private InputStream inStream = null;
  boolean polling = false;
  boolean showRaw = false;

  byte adr = 1;
  int polCnts = 0;
  // Well known SPP UUID
  private static final UUID MY_UUID =
          UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

  // Insert your server's MAC address
//  private static String address = "00:00:00:00:00:00";
  private String address = "00:12:6F:00:7C:94";
  private byte gAdr = 1;

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

    showRawCkBx = (CheckBox) findViewById(R.id.checkBox);
    showRawCkBx.setChecked(showRaw);

    Button button = (Button) findViewById(R.id.button1);
    Button set_DoneButton = (Button) findViewById(R.id.set_Done);

    Spinner spinner = (Spinner) findViewById(R.id.spinner);
    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
            R.array.protocols_array, android.R.layout.simple_spinner_item);

// Apply the adapter to the spinner
    spinner.setAdapter(adapter);


    set_DoneButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        String s = gAdrEdTxt.getText().toString().trim();
        adr = (byte) Integer.parseInt(s);
        showRaw = showRawCkBx.isChecked();
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


    out.append("\n...In onCreate()...");

    btAdapter = BluetoothAdapter.getDefaultAdapter();
    CheckBTState();

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (polling) poll();
        mHandler.postDelayed(this, 500);
      }
    };
    mHandler.post(runnable);
  }

  public void poll() {
    int[] tic = new int[2];
    int rxCnt = 0;
    int lev = 0;
    int tmp = 0;
    //  out.setText("");
    byte[] buf = new byte[2];
    for (int w = 0; w < 1; ++w) {
      try {
        if (inStream == null) return;

        while (inStream.available() > 0)
          rxCnt = inStream.read(buf, 0, 1);
        polCnts++;

        buf[0] = (byte) (0x80 | adr);
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
            if (showRaw)
              out.append(String.format(" Rx:[%02X][%02X]", buf[0], buf[1]));

            while (inStream.available() > 0)
              rxCnt = inStream.read(buf, 0, 1);
            polCnts++;

            buf[0] = (byte) (0x80 | adr);
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
              }
            }
            if (polCnts < 0) polCnts = 1;
            if ((polCnts % 25) == 0) out.setText("");

            out.append(String.format("\n%5d Level = %6.3f, Temp %5.1f, tic=%d,%d", polCnts, (float) lev / 384, ((float) tmp) * 0.2, tic[0], tic[1]));

          }
        } else {
          out.append("\n...No-Comm...");

        }

      } catch (IOException e) {
        polling = false;
        String msg = "In Poll() and an exception occurred during write: " + e.getMessage();
        msg = msg + ".\n\nCheck that the SPP UUID: " + MY_UUID.toString() + " exists on server.\n\n";
        out.append(msg);
        // AlertBox("Fatal Error", msg);
        return;
      }
    }
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
    polling = false;
    out.append("\n...In onResume...\n...Attempting client connect...");

    // Set up a pointer to the remote node using it's address.
    BluetoothDevice device = btAdapter.getRemoteDevice(address);

    // Two things are needed to make a connection:
    //   A MAC address, which we got above.
    //   A Service ID or UUID.  In this case we are using the
    //     UUID for SPP.
    try {
      btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
    } catch (IOException e) {
      AlertBox("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
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
        AlertBox("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
      }
    }

    // Create a data stream so we can talk to server.
    out.append("\n...Sending message to server...");

    try {
      outStream = btSocket.getOutputStream();
      inStream = btSocket.getInputStream();

    } catch (IOException e) {
      AlertBox("Fatal Error", "In onResume() and output stream creation failed:" + e.getMessage() + ".");
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

  @Override
  public void onPause() {
    super.onPause();

    polling = false;
    out.append("\n...In onPause()...");

    if (outStream != null) {
      try {
        outStream.flush();
      } catch (IOException e) {
        AlertBox("Fatal Error", "In onPause() and failed to flush output stream: " + e.getMessage() + ".");
      }
    }

    try {
      btSocket.close();
    } catch (IOException e2) {
      AlertBox("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
    }
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
                finish();
              }
            }).show();
  }
}

