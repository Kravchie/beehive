package com.example.iotui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class StartExpActivity extends AppCompatActivity {

    private String TAG = "Tag";
    private String participantName;
    private MqttAndroidClient client;
    private MqttConnectOptions options;
    private Button buttonStart;
    private Button buttonEnd;
    private ProgressBar progressBar;
    private TextView textViewTrial;
    private int trialCounter;
    private int progressStatus;
    private int maxDev;
    private boolean flag;
    private Handler handler;
    Set<String> activeDevicesSet = new HashSet<>();
    public static final String BROKER = "XXX";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_exp);

        handler = new Handler(); //to update UI from my mqtt callback
        flag = false;

        buttonStart = findViewById(R.id.buttonStart);
        buttonStart.setText("START");
        buttonStart.setVisibility(View.GONE);
        buttonStart.setEnabled(true);
        progressBar = findViewById(R.id.progressBar);
        buttonEnd = findViewById(R.id.buttonEnd);

        textViewTrial = findViewById(R.id.textViewTrial);

        trialCounter = 1;
        progressStatus = 0;

        Bundle bundle = getIntent().getExtras();
        if(bundle != null){
            participantName = bundle.getString("name");
            maxDev = Integer.parseInt(bundle.getString("dev_number"));
        }

        progressBar.setMax(maxDev);

        textViewTrial.setText("Trial: " + trialCounter);
        progressBar.setProgress(progressStatus);

        connectToBroker();

    }

    private void optionSetup() {
        try {
            options = new MqttConnectOptions();

            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            InputStream caInput = new BufferedInputStream(getResources().openRawResource(R.raw.mosqserv));
            Certificate ca;

            ca = cf.generateCertificate(caInput);
            caInput.close();

            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);

            options.setCleanSession(false);
            options.setSocketFactory(context.getSocketFactory());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }


    }

    public void endExpRound(View view){
        String mess = participantName + ";" + trialCounter;
        MqttMessage message1 = new MqttMessage(mess.getBytes());
        message1.setQos(2);
        message1.setRetained(false);
        try {
            client.publish("android/stop", message1);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void beginExpRound(View view){
        try {
            if (buttonStart.getText().equals("START")) {
                flag = true;
                checkIfSensorsActive();
                buttonStart.setText("STOP");
            } else{
                flag = false;
                String mess = participantName + ";" + trialCounter;
                MqttMessage message1 = new MqttMessage(mess.getBytes());
                message1.setQos(2);
                message1.setRetained(false);
                client.publish("android/stop", message1);
                buttonStart.setText("START");
                trialCounter += 1;
                textViewTrial.setText("Trial: " + trialCounter);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "Message published");
    }

    private void checkIfSensorsActive() throws MqttException {
        progressStatus = 0;
        MqttMessage message = new MqttMessage("active?".getBytes());
        message.setQos(2);
        message.setRetained(false);
        client.publish("init", message);
        Handler handler2 = new Handler();
        handler2.post(new Runnable() {
            public void run() {
                progressBar.setProgress(progressStatus);
                progressBar.setVisibility(View.VISIBLE);
                buttonStart.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void connectToBroker() {
        String clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(this.getApplicationContext(), BROKER, clientId);

        try {
            optionSetup();

            IMqttToken token = client.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.v(TAG, "onSuccess");
                    try {
                        MqttMessage message = new MqttMessage("active?".getBytes());
                        message.setQos(2);
                        message.setRetained(false);
                        client.publish("init", message);
                        listenForActive();
                    } catch (MqttException e) {
                        Log.v(TAG, "warning");
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "onFailure");

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void listenForActive() throws MqttException {
        client.subscribe("active/#", 2, new IMqttMessageListener() {
            public void messageArrived(String topic, MqttMessage message) {
                String[] topics = topic.split("/");
                activeDevicesSet.add(topics[1]);
                Log.i(TAG, "Inside messageArrived: " + activeDevicesSet + " " + participantName);
                progressStatus++;
                handler.post(new Runnable() {
                    public void run() {
                        Log.i(TAG, "Inside run for: " + activeDevicesSet + " " + participantName);
                        progressBar.setProgress(progressStatus);
                        if(progressStatus == progressBar.getMax() && activeDevicesSet.size() == progressStatus){
                            activeDevicesSet.clear();
                            progressBar.setVisibility(View.INVISIBLE);
                            buttonStart.setVisibility(View.VISIBLE);
                            progressStatus = 0;
                            if(flag){
                                String mess = participantName + ";" + trialCounter;
                                Log.i(TAG, "Inside flag true: " + activeDevicesSet + " " + participantName);
                                MqttMessage message1 = new MqttMessage(mess.getBytes());
                                message1.setQos(2);
                                message1.setRetained(false);
                                try {
                                    client.publish("android/start", message1);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }
                                flag = false;

                            }
                        }
                    }
                } );

            }
        });



    }
}
