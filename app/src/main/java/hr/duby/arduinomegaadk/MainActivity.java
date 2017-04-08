package hr.duby.arduinomegaadk;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;


import java.io.FileDescriptor;
import android.content.IntentFilter;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    //widgets
    private Button btnCurrent;
    private TextView tvMotorSpeed;
    private SeekBar seekBarRight;
    private SeekBar seekBarLeft;
    private ListView m_list_view;

    //CONST
    private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";

    //VARS
    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;
    private ArrayAdapter<String> m_adapter;
    private ArrayList<String> msgCodeList;

    private int mSpeedL = 0;  //initial value -> pwm value (0-1024)
    private int mSpeedR = 0;  //initial value -> pwm value (0-1024)
    private boolean isRegisteredUSB = false;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    //UsbAccessory accessory = UsbManager.getAccessory(intent);
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        // USB permission denied
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                //UsbAccessory accessory = UsbManager.getAccessory(intent);
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                if (accessory != null && accessory.equals(mAccessory)) {
                    //accessory detached
                    closeAccessory();
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvMotorSpeed = (TextView) findViewById(R.id.tvMotorSpeed);
        seekBarLeft = (SeekBar) findViewById(R.id.seekBarLeft);
        seekBarRight = (SeekBar) findViewById(R.id.seekBarRight);
        btnCurrent = (Button) findViewById(R.id.btnCurrent);

        mSBLeftChangeListener();
        mSBRightChangeListener();
        seekBarLeft.setMax(99);  //pwm (0-255)
        seekBarRight.setMax(99);  //pwm (0-255)
        seekBarLeft.setProgress(mSpeedL);
        seekBarRight.setProgress(mSpeedR);

        btnCurrent.setOnClickListener(this);

        msgCodeList = new ArrayList<String>();
        m_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, msgCodeList);
        m_list_view = (ListView) findViewById(R.id.id_list);
        m_list_view.setAdapter(m_adapter);

        setupAccessory();
    }

    @Override
    public void onResume() {
        super.onResume();
        //sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        //isRegisteredSensor = true;

        if (mInputStream != null && mOutputStream != null) {
            return;  //streams were not null");
        }
        //streams were null");
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory, mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            updateMsgList("onResume -> mAccessory is NULL!");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        closeAccessory();
        if (isRegisteredUSB){
            unregisterReceiver(mUsbReceiver);
            isRegisteredUSB = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isRegisteredUSB){
            unregisterReceiver(mUsbReceiver);
            isRegisteredUSB = false;
        }
    }

    @Override
    protected void onStop() {
        if (isRegisteredUSB){
            unregisterReceiver(mUsbReceiver);
            isRegisteredUSB = false;
        }
        super.onStop();
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            ValueMsg t = (ValueMsg) msg.obj;
            // this is where you handle the data you sent. You get it by calling the getReading() function
            //tvArduinoResponse.setText();
            updateMsgList("Flag: "+t.getFlag()+"; Reading: "+t.getReading()+"; Date: "+(new Date().toString()));
        }
    };


    private void setupAccessory() {
        updateMsgList("setupAccessory");
        //mUsbManager = UsbManager.getInstance(this);
        mUsbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        mPermissionIntent =PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        if (getLastNonConfigurationInstance() != null) {
            mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
            openAccessory(mAccessory);
        }
        if (mAccessory != null){
            updateMsgList("mAccessory: " + mAccessory.toString());
        }else{
            updateMsgList("mAccessory is NULL!");
        }


    }

    private void openAccessory(UsbAccessory accessory) {
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        updateMsgList("openAccessory -> mFileDescriptor: " + mFileDescriptor.toString());
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            updateMsgList("openAccessory -> mFileDescriptor.getFileDescriptor: " + fd.toString());
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            Thread thread = new Thread(null, commRunnable, "OpenAccessoryTest");
            thread.start();
            updateMsgList("openAccessory -> OK");
        } else {
            updateMsgList("openAccessory -> FAILED");
        }
    }

    private void closeAccessory() {
        updateMsgList("closeAccessory");
        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    Runnable commRunnable = new Runnable() {
        @Override
        public void run() {
            int ret = 0;
            byte[] buffer = new byte[6];
            while (ret >= 0) {
                try {
                    ret = mInputStream.read(buffer);
                } catch (IOException e) {
                    updateMsgList("IOException " + e);
                    break;
                }
                switch (buffer[0]) {
                    case Const.CMD_CURRENT:
                        if (buffer[1] == Const.LMOTOR) {
                            final float temperatureValue = (((buffer[2] & 0xFF) << 24) + ((buffer[3] & 0xFF) << 16) + ((buffer[4] & 0xFF) << 8) + (buffer[5] & 0xFF));
                            updateMsgList("temperatureValue " + temperatureValue);
                        }
                        break;
                    default:
                        updateMsgList("unknown msg " +  buffer[0]);
                        break;
                }
            }
        }
    };


    /*
    public void run() {
        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;

        while (true) { // read data
            try {
                ret = mInputStream.read(buffer);
            } catch (IOException e) {
                break;
            }

            i = 0;
            while (i < ret) {
                int len = ret - i;
                if (len >= 1) {
                    Message m = Message.obtain(mHandler);
                    int value = (int)buffer[i];
                    // 'f' is the flag, use for your own logic
                    // value is the value from the arduino
                    m.obj = new ValueMsg('f', value);
                    mHandler.sendMessage(m);
                }
                i += 1; // number of bytes sent from arduino
            }

        }
    }*/

    private void updateMsgList(final String msg_code) {
        DLog(msg_code);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msgCodeList.add(msg_code);
                m_adapter.notifyDataSetChanged();
            }
        });
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnCurrent:
                executeCMD(Const.CMD_CURRENT, Const.LMOTOR, 0);
                break;
        }
    }

    //**********************************************************************************************
    private void mSBLeftChangeListener() {
        seekBarLeft.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSpeedL = seekBar.getProgress();
                updateMsgList("seekBar LEFT position: " + mSpeedL);

                //moveMotorCommand(Const.LMOTOR, mSpeedL);
                executeCMD(Const.CMD_MOTOR, Const.LMOTOR, mSpeedL);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvMotorSpeed.setText(getString(R.string.motorSpeed, mSpeedL));
                    }
                });
            }
        });
    }

    private void mSBRightChangeListener() {
        seekBarRight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSpeedR = seekBar.getProgress();
                updateMsgList("seekBar RIGHT position: " + mSpeedR);

                //moveMotorCommand(Const.RMOTOR, mSpeedR);
                executeCMD(Const.CMD_MOTOR, Const.RMOTOR, mSpeedR);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvMotorSpeed.setText(getString(R.string.motorSpeed, mSpeedR));
                    }
                });
            }
        });
    }

    /*
    public void moveMotorCommand(byte target, int value) {
        //updateMsgList("moveMotorCommand");
        DLog("moveMotorCommand");
        byte[] buffer = new byte[3];
        buffer[0] = Const.CMD_MOTOR;
        buffer[1] = target;
        buffer[2] = (byte) value;
        if (mOutputStream != null) {
            try {
                mOutputStream.write(buffer);
                String niceMsg = "CMD_MOTOR, " + (target == Const.LMOTOR ? "LEFT" : "RIGHT, SPEED: " + value);
                updateMsgList("CMD to send: -> " + niceMsg);
            } catch (IOException e) {
                //Log.e("DTag", "write failed", e);
                ELog("write failed: " + e);
            }
        }
    }*/

    // CMD_GROUP  -> CMD_CURRENT (CMD_MOTOR)
    // CMD_TARGET -> RMOTOR (LMOTOR)
    // CMD_VALUE  -> motor speed,... (optional)
    private void executeCMD(int CMD_GROUP, int CMD_TARGET, int CMD_VALUE) {
        DLog("executeCMD_Current");
        byte[] buffer = new byte[3];
        buffer[0] = (byte) CMD_GROUP;
        buffer[1] = (byte) CMD_TARGET;
        buffer[2] = (byte) CMD_VALUE;
        if (mOutputStream != null) {
            try {
                mOutputStream.write(buffer);
                String niceMsg = readableCMDFrom( CMD_GROUP, CMD_TARGET, CMD_VALUE);
                updateMsgList("CMD to send: -> " + niceMsg);
            } catch (IOException e) {
                //Log.e("DTag", "write failed", e);
                ELog("write failed: " + e);
            }
        }
    }

    private String readableCMDFrom(int CMD_GROUP, int CMD_TARGET, int CMD_VALUE){
        //"CMD_MOTOR, " + (target == Const.LMOTOR ? "LEFT" : "RIGHT, SPEED: " + value);
        String CMD_GROUPStr = "";
        String CMD_TARGETStr = "";
        String CMD_VALUEStr = "" + CMD_VALUE;

        switch (CMD_GROUP){
            case Const.CMD_MOTOR:
                CMD_GROUPStr = "CMD_MOTOR";
                break;
            case Const.CMD_CURRENT:
                CMD_GROUPStr = "CMD_CURRENT";
                break;
        }

        switch (CMD_TARGET){
            case Const.LMOTOR:
                CMD_TARGETStr = "LMOTOR";
                break;
            case Const.RMOTOR:
                CMD_TARGETStr = "RMOTOR";
                break;
        }

        return CMD_GROUPStr + ", " + CMD_TARGETStr + ", " + CMD_VALUEStr;
    }

    //**********************************************************************************************
    private void DLog(String msg) {
        String className = this.getClass().getSimpleName();
        Log.d("DTag", className + ": " + msg);

    }

    private void ELog(String msg) {
        String className = this.getClass().getSimpleName();
        Log.e("DTag", className + ": " + msg);

    }


}
