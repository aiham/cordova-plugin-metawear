package com.mbientlab.metawear.cordova;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;
import android.content.ServiceConnection;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import android.content.ComponentName;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.util.Log;
import java.util.HashMap;
import static com.mbientlab.metawear.MetaWearBoard.ConnectionStateHandler;
import org.json.JSONArray;
import org.json.JSONException;

import android.os.IBinder;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.RouteManager.MessageHandler;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Gpio.AnalogReadMode;
import com.mbientlab.metawear.module.Gpio.PullMode;

/**
 *
 * Created by Lance Gleason of Polyglot Programming LLC. on 10/11/2015.
 * http://www.polyglotprogramminginc.com
 * https://github.com/lgleasain
 * Twitter: @lgleasain
 *
 */

public class MWDevice extends CordovaPlugin implements ServiceConnection{
    public static final String TAG = "com.mbientlab.metawear.cordova";

    public static final String INITIALIZE = "initialize";
    public static final String SCAN_FOR_DEVICES = "scanForDevices";
    public static final String CONNECT = "connect";
    public static final String DISCONNECT = "disconnect";
    public static final String READ_RSSI = "readRssi";
    public static final String START_ACCELEROMETER = "startAccelerometer";
    public static final String STOP_ACCELEROMETER = "stopAccelerometer";
    public static final String GPIO_READ_ANALOG = "gpioReadAnalogIn";
    public static final String GPIO_READ_DIGITAL = "gpioReadDigitalIn";
    private MetaWearBleService.LocalBinder serviceBinder;

    private String mwMacAddress;
    private MetaWearBoard mwBoard;
    private HashMap<String, CallbackContext> mwCallbackContexts;
    private boolean initialized = false;
    private RSSI rssi;
    private MWAccelerometer mwAccelerometer;
    private BluetoothScanner bluetoothScanner;
    private GpioModule gpioModule;
    
    /**
     * Constructor.
     */
    public MWDevice() {}
    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        rssi = new RSSI(this);
        mwAccelerometer = new MWAccelerometer(this);
        gpioModule = new GpioModule(this);
        mwCallbackContexts = new HashMap<String, CallbackContext>(); 
        bluetoothScanner = new BluetoothScanner(this);
        Context applicationContext = cordova.getActivity().getApplicationContext();
        applicationContext.bindService(
                                       new Intent(cordova.getActivity(),
                                                  MetaWearBleService.class),
                                       this, Context.BIND_AUTO_CREATE
                                       );
        Log.v(TAG,"Init Device");
    }

    public HashMap<String, CallbackContext> getMwCallbackContexts(){
        return mwCallbackContexts;
    }

    public MetaWearBoard getMwBoard(){
        return mwBoard;
    }

    public boolean execute(final String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        final int duration = Toast.LENGTH_SHORT;
        // Shows a toast
        Log.v(TAG,"mwDevice received:"+ action);
        if(action.equals(INITIALIZE)){
            mwCallbackContexts.put(INITIALIZE, callbackContext);
            return true;
        } else if(action.equals(CONNECT)){
            mwCallbackContexts.put(CONNECT, callbackContext);
            mwMacAddress = (String) args.get(0);
            if(initialized){
                connectBoard();
            }
            return true;
        } else if(action.equals(SCAN_FOR_DEVICES)){
            mwCallbackContexts.put(SCAN_FOR_DEVICES, callbackContext);
            if(initialized){
                bluetoothScanner.startBleScan();
            }
            return true;
        }else if(action.equals(DISCONNECT)){
            mwBoard.disconnect();
            return true;
        } else if(action.equals(READ_RSSI)){
            mwCallbackContexts.put(READ_RSSI, callbackContext);
            rssi.readRssi();
            return true;
        } else if(action.equals(START_ACCELEROMETER)){
            mwCallbackContexts.put(START_ACCELEROMETER, callbackContext);
            mwAccelerometer.startAccelerometer();
            return true;
        } else if(action.equals(STOP_ACCELEROMETER)){
            mwAccelerometer.stopAccelerometer();
            return true;
        } else if(action.equals(GPIO_READ_ANALOG)){
            int pin = (Integer) args.get(0);
            AnalogReadMode analogReadMode = AnalogReadMode.ADC;
            String passedInAnalogReadMode = (String) args.get(2);
            
            if((passedInAnalogReadMode != null) &&
               (passedInAnalogReadMode.equals("ABS_REFERENCE"))){
                analogReadMode = AnalogReadMode.ABS_REFERENCE;
            }
            PullMode pullMode = PullMode.NO_PULL;
            String passedInPullMode = (String) args.get(1);
            if(passedInPullMode != null){
                if(passedInPullMode.equals("PULL_UP")){
                    pullMode = PullMode.PULL_UP;
                }else if (passedInPullMode.equals("PULL_DOWN")){
                    pullMode = PullMode.PULL_DOWN;
                }
            }
            String pinString = String.valueOf(pin);
            mwCallbackContexts.put(GPIO_READ_ANALOG + pinString, callbackContext);
            gpioModule.readAnalogIn(pin, pullMode, analogReadMode);
            return true;
        }
        else{
            return false;}
    }

    @Override
    public void onDestroy(){
        cordova.getActivity().getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service){
        serviceBinder = (MetaWearBleService.LocalBinder) service;
        Log.i("MWDevice", "Service Connected");
        initialized = true;
        if(mwCallbackContexts.get(CONNECT) != null){
            connectBoard();
        }else if(mwCallbackContexts.get(SCAN_FOR_DEVICES) != null){
            bluetoothScanner.startBleScan();
        }

        if(mwCallbackContexts.get(CONNECT) == null &&
           mwCallbackContexts.get(SCAN_FOR_DEVICES) == null)
        {
            mwCallbackContexts.get(INITIALIZE).sendPluginResult(new PluginResult(PluginResult.Status.OK,
                                                                             "initialized"));
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {}



    public MetaWearBoard retrieveBoard() {
        final BluetoothManager btManager= 
            (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice= 
            btManager.getAdapter().getRemoteDevice(mwMacAddress);

        // Create a MetaWear board object for the Bluetooth Device
        mwBoard= serviceBinder.getMetaWearBoard(remoteDevice);
        return(mwBoard);
    }

    private final ConnectionStateHandler stateHandler = new ConnectionStateHandler() {
            @Override
            public void connected() {
                Log.i("MainActivity", "Connected");
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,
                                                             "CONNECTED");
                pluginResult.setKeepCallback(true);
                mwCallbackContexts.get(CONNECT).sendPluginResult(pluginResult);
            }

            @Override
            public void disconnected() {
                Log.i("MainActivity", "Connected Lost");
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,
                                                             "DISCONNECTED");
                pluginResult.setKeepCallback(true);
                mwCallbackContexts.get(CONNECT).sendPluginResult(pluginResult);
            }

            @Override
            public void failure(int status, Throwable error) {
                Log.e("MainActivity", "Error connecting", error);
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK,
                                                             "ERROR");
                pluginResult.setKeepCallback(true);
                mwCallbackContexts.get(CONNECT).sendPluginResult(pluginResult);
            }
        };

    public void connectBoard() {
        mwBoard = retrieveBoard();
        mwBoard.setConnectionStateHandler(stateHandler);
        mwBoard.connect();
    }
}
