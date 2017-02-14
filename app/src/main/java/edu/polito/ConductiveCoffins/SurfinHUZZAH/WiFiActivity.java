package edu.polito.ConductiveCoffins.SurfinHUZZAH;

import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.view.WindowManager;
import android.os.AsyncTask;
import android.view.MotionEvent;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import org.billthefarmer.mididriver.MidiDriver;

/**
 * Application to establish a connection with Huzzah ESP8266 that receive data from a
 * Cypress Capsense CY8CMBR3110-SX2I, Accelerometer and Microphone.
 * The app acts as a MIDI synthesizer/sequencer.
 *
 * Using as starting point:
 * how to open a serial communication link to a remote host over WiFi.
 * by Hayk Martirosyan
 */
public class WiFiActivity extends Activity
implements MidiDriver.OnMidiStartListener, AdapterView.OnItemSelectedListener
{
    private TabHost tabHost;
    private float lastX;


    // Tag for logging
    private final String TAG = getClass().getSimpleName();

    // AsyncTask object that manages the connection in a separate thread
    WiFiSocketTask wifiTask = null;

    // UI elements
    TextView textStatus, textRX, textScale;
    EditText editTextAddress, editTextPort;
    Button buttonConnect, buttonDisconnect, buttonOctavePlus, buttonOctaveMinus;
    Spinner spinnerKeys, spinnerWind, spinnerNotes, spinnerScale;

    //MIDI elements
    protected MidiDriver midi;

    protected ArduinoMidiConverter arduinoMidiConverter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wi_fi);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);



        tabHost = (TabHost)findViewById(R.id.tabHost);
        tabHost.setup();

        final TabWidget tabWidget = tabHost.getTabWidget();
        final FrameLayout tabContent = tabHost.getTabContentView();

        // Get the original tab textviews and remove them from the viewgroup.
        TextView[] originalTextViews = new TextView[tabWidget.getTabCount()];
        for (int index = 0; index < tabWidget.getTabCount(); index++) {
            originalTextViews[index] = (TextView) tabWidget.getChildTabViewAt(index);

        }
        tabWidget.removeAllViews();

        // Ensure that all tab content childs are not visible at startup.
        for (int index = 0; index < tabContent.getChildCount(); index++) {
            tabContent.getChildAt(index).setVisibility(View.GONE);
        }

        // Create the tabspec based on the textview childs in the xml file.
        // Or create simple tabspec instances in any other way...
        for (int index = 0; index < originalTextViews.length; index++) {
            final TextView tabWidgetTextView = originalTextViews[index];
            final View tabContentView = tabContent.getChildAt(index);
            TabSpec tabSpec = tabHost.newTabSpec((String) tabWidgetTextView.getTag());
            tabSpec.setContent(new TabContentFactory() {
                @Override
                public View createTabContent(String tag) {
                    return tabContentView;
                }
            });
            if (tabWidgetTextView.getBackground() == null) {
                tabSpec.setIndicator(tabWidgetTextView.getText());
            } else {
                tabSpec.setIndicator(tabWidgetTextView.getText(), tabWidgetTextView.getBackground());
            }
            tabHost.addTab(tabSpec);
        }

		tabHost.setCurrentTab(0);

        // Save references to UI elements
        textStatus = (TextView)findViewById(R.id.textStatus);
        textRX = (TextView)findViewById(R.id.textRX);
        textScale = (TextView)findViewById(R.id.textScale);
        editTextAddress = (EditText)findViewById(R.id.address);
        editTextPort = (EditText)findViewById(R.id.port);
        buttonConnect = (Button)findViewById(R.id.connect);
        buttonDisconnect = (Button)findViewById(R.id.disconnect);
        buttonDisconnect.setEnabled(false);
        spinnerKeys = (Spinner) findViewById(R.id.keyboard);
        spinnerWind = (Spinner) findViewById(R.id.wind);
        buttonOctavePlus = (Button) findViewById(R.id.octavePlusBtn);
        buttonOctaveMinus = (Button) findViewById(R.id.octaveMinusBtn);
        spinnerNotes = (Spinner) findViewById(R.id.rootNote);
        spinnerScale = (Spinner) findViewById(R.id.scaleSpinner);

        // Set listener on spinners
        spinnerKeys.setOnItemSelectedListener(this);
        spinnerWind.setOnItemSelectedListener(this);
        spinnerNotes.setOnItemSelectedListener(this);
        spinnerScale.setOnItemSelectedListener(this);

        // Create midi driver
        midi = new MidiDriver();
        arduinoMidiConverter = new ArduinoMidiConverter();

        textScale.setText(String.valueOf(arduinoMidiConverter.getOctave()));

        // Set on midi start listener
        if (midi != null)
            midi.setOnMidiStartListener(this);

    }

    // On resume

    @Override
    protected void onResume()
    {
        super.onResume();

        // Start midi

        if (midi != null)
            midi.start();
    }

    // On pause

    @Override
    protected void onPause() {
        super.onPause();

        // Stop midi

        if (midi != null)
            midi.stop();
    }


    public void switchTabs(boolean direction) {
        if (!direction) // move left
        {
            if (tabHost.getCurrentTab() == 0){
                //tabHost.setCurrentTab(tabHost.getTabWidget().getTabCount() - 1);

            }
            else
                tabHost.setCurrentTab(tabHost.getCurrentTab() - 1);
        } else
        // move right
        {
            if (tabHost.getCurrentTab() != (tabHost.getTabWidget()
                    .getTabCount() - 1))
                tabHost.setCurrentTab(tabHost.getCurrentTab() + 1);

        }
    }


    /**
     * Helper function, print a status to both the UI and program log.
     */
    void setStatus(String s) {
        Log.v(TAG, s);
        textStatus.setText(s);
    }

    /**
     * Try to start a connection with the specified remote host.
     */
    public void connectButtonPressed(View v) {

        if(wifiTask != null) {
            setStatus("Already connected!");
            return;
        }

        try {
            // Get the remote host from the UI and start the thread
            String host = editTextAddress.getText().toString();
            int port = Integer.parseInt(editTextPort.getText().toString());

            // Start the asyncronous task thread
            setStatus("Attempting to connect...");
            wifiTask = new WiFiSocketTask(host, port);
            wifiTask.execute();

        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Invalid address/port!");
        }
    }

    /**
     * Disconnect from the connection.
     */
    public void disconnectButtonPressed(View v) {

        if(wifiTask == null) {
            setStatus("Already disconnected!");
            return;
        }

        wifiTask.disconnect();
        setStatus("Disconnecting...");
    }

    /**
     * Octave change when octaveButton is pressed
     */
    public void octaveButtonPlusPressed(View v) {
        arduinoMidiConverter.setOctave(true);
        arduinoMidiConverter.chooseMyNotes(String.valueOf(spinnerNotes.getSelectedItem()), String.valueOf(spinnerScale.getSelectedItem()));
        textScale.setText(String.valueOf(arduinoMidiConverter.getOctave()));

    }

    public void octaveButtonMinusPressed(View v) {
        arduinoMidiConverter.setOctave(false);
        arduinoMidiConverter.chooseMyNotes(String.valueOf(spinnerNotes.getSelectedItem()), String.valueOf(spinnerScale.getSelectedItem()));
        textScale.setText(String.valueOf(arduinoMidiConverter.getOctave()));

    }

    /**
     * Invoked by the AsyncTask when the connection is successfully established.
     */
    private void connected() {
        setStatus("Connected.");
        editTextAddress.setEnabled(false);
        editTextPort.setEnabled(false);
        buttonConnect.setEnabled(false);
        buttonDisconnect.setEnabled(true);

    }


    /**
     * Invoked by the AsyncTask when the connection ends..
     */
    private void disconnected() {
        setStatus("Disconnected.");
        buttonConnect.setEnabled(true);
        buttonDisconnect.setEnabled(false);

        textRX.setText("");
        wifiTask = null;

        editTextAddress.setEnabled(true);
        editTextPort.setEnabled(true);
    }

    /**
     * Invoked by the AsyncTask when a newline-delimited message is received.
     */
    private void gotMessage(String msg) {
        if ((arduinoMidiConverter.isAllOff())){
            buttonOctaveMinus.setEnabled(true);
            buttonOctavePlus.setEnabled(true);
            spinnerKeys.setEnabled(true);
            spinnerWind.setEnabled(true);
            spinnerNotes.setEnabled(true);
            spinnerScale.setEnabled(true);
            ;
        }
        else {
            buttonOctaveMinus.setEnabled(false);
            buttonOctavePlus.setEnabled(false);
            spinnerKeys.setEnabled(false);
            spinnerWind.setEnabled(false);
            spinnerNotes.setEnabled(false);
            spinnerScale.setEnabled(false);
        }

        textRX.setText(msg);
        Log.v(TAG, "MSG = " + msg);
        arduinoMidiConverter.retrieveFromWifi(msg);
        playMidi();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

        arduinoMidiConverter.chooseMyInstruments(String.valueOf(spinnerKeys.getSelectedItem()), String.valueOf(spinnerWind.getSelectedItem()));
        arduinoMidiConverter.chooseMyNotes(String.valueOf(spinnerNotes.getSelectedItem()), String.valueOf(spinnerScale.getSelectedItem()));
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    // Implementing touch event for view flipper

    @Override
    public boolean onTouchEvent(MotionEvent touchevent) {
        switch (touchevent.getAction()) {
            // when user first touches the screen to swap
            case MotionEvent.ACTION_DOWN: {
                lastX = touchevent.getX();
                break;
            }
            case MotionEvent.ACTION_UP: {
                float currentX = touchevent.getX();

                // if left to right swipe on screen
                if (lastX < currentX) {

                    switchTabs(false);
                }

                // if right to left swipe on screen
                if (lastX > currentX) {
                    switchTabs(true);
                }

                break;
            }
        }
        return false;
    }

    /**
     * AsyncTask that connects to a remote host over WiFi and reads/writes the connection
     * using a socket. The read loop of the AsyncTask happens in a separate thread, so the
     * main UI thread is not blocked. However, the AsyncTask has a way of sending data back
     * to the UI thread. Under the hood, it is using Threads and Handlers.
     */
    public class WiFiSocketTask extends AsyncTask<Void, String, Void> {

        // Location of the remote host
        String address;
        int port;

        // Special messages denoting connection status
        private static final String PING_MSG = "SOCKET_PING";
        private static final String CONNECTED_MSG = "SOCKET_CONNECTED";
        private static final String DISCONNECTED_MSG = "SOCKET_DISCONNECTED";

        Socket socket = null;
        BufferedReader inStream = null;
        OutputStream outStream = null;

        // Signal to disconnect from the socket
        private boolean disconnectSignal = false;

        // Socket timeout - close if no messages received (ms)
        private int timeout = 5000;

        // Constructor
        WiFiSocketTask(String address, int port) {
            this.address = address;
            this.port = port;
        }

        /**
         * Main method of AsyncTask, opens a socket and continuously reads from it
         */
        @Override
        protected Void doInBackground(Void... arg) {

            try {

                // Open the socket and connect to it
                socket = new Socket();
                socket.connect(new InetSocketAddress(address, port), timeout);

                // Get the input and output streams
                inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                outStream = socket.getOutputStream();

                // Confirm that the socket opened
                if(socket.isConnected()) {

                    // Make sure the input stream becomes ready, or timeout
                    long start = System.currentTimeMillis();
                    while(!inStream.ready()) {
                        long now = System.currentTimeMillis();
                        if(now - start > timeout) {
                            Log.e(TAG, "Input stream timeout, disconnecting!");
                            disconnectSignal = true;
                            break;
                        }
                    }
                } else {
                    Log.e(TAG, "Socket did not connect!");
                    disconnectSignal = true;
                }

                // Read messages in a loop until disconnected
                while(!disconnectSignal) {

                    // Parse a message with a newline character
                    String msg = inStream.readLine();

                    // Send it to the UI thread
                    publishProgress(msg);
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Error in socket thread!");
            }

            // Send a disconnect message
            publishProgress(DISCONNECTED_MSG);

            // Once disconnected, try to close the streams
            try {
                if (socket != null) socket.close();
                if (inStream != null) inStream.close();
                if (outStream != null) outStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        /**
         * This function runs in the UI thread but receives data from the
         * doInBackground() function running in a separate thread when
         * publishProgress() is called.
         */
        @Override
        protected void onProgressUpdate(String... values) {

            String msg = values[0];
            if(msg == null) return;

            // Handle meta-messages
            if(msg.equals(CONNECTED_MSG)) {
                connected();
            } else if(msg.equals(DISCONNECTED_MSG))
                disconnected();
            else if(msg.equals(PING_MSG))
            {}

            // Invoke the gotMessage callback for all other messages
            else
                gotMessage(msg);


            super.onProgressUpdate(values);
        }


        /**
         * Set a flag to disconnect from the socket.
         */
        public void disconnect() {
            disconnectSignal = true;

        }
    }

    // Listener for sending initial midi messages.
    @Override
    public void onMidiStart()
    {
        // Program change - harpsicord
        sendMidi(0xc0, 6);

    }

    // Send a midi message methods

    protected void sendMidi(int m, int p)
    {
        byte msg[] = new byte[2];

        msg[0] = (byte) m;
        msg[1] = (byte) p;

        midi.write(msg);
    }

    protected void sendMidi(int m, int n, int v)
    {
        byte msg[] = new byte[3];

        msg[0] = (byte) m;
        msg[1] = (byte) n;
        msg[2] = (byte) v;

        midi.write(msg);
    }

    //Read notes

    protected void playMidi(){

        sendMidi(0xc0, arduinoMidiConverter.getInstrument());

        ArrayList<MidiNote> notes = arduinoMidiConverter.getNotes();
        if (notes.size()!=0) {
            for (int i = 0; i < notes.size(); i++) {
                sendMidi(notes.get(i).getChannel(), notes.get(i).getPitch(), notes.get(i).getVelocity());
            }
        }
        sendMidi(0xb0,0x07,arduinoMidiConverter.getVolume());
        sendMidi(0xE0,arduinoMidiConverter.getProximityLSB(),arduinoMidiConverter.getProximityMSB());
    }
}
