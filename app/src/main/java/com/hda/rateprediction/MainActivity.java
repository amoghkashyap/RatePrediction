package com.hda.rateprediction;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    TextView textTimeDisplay;
    Button buttonStartResume, buttonStop, buttonSaveToCsv;

    int timeCount = 0;
    int level, rsrp, rsrq, rssnr, cqi, asuLevel, timingAdvance, dbm, ci, earFcn, tac, pci;
    double latitude, longitude;
    boolean isRunning, isFirstCSVWrite = true, isFirstOperatorDisplay = true;
    Timestamp timestamp;

    static String TAG = "hda";
    ArrayList<String[]> networkDataList = new ArrayList<>();
    LineGraphSeries<DataPoint> rsrpLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> rsrqLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> rssnrLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> latitudeLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    LineGraphSeries<DataPoint> longitudeLine = new LineGraphSeries<>(new DataPoint[]{new DataPoint(0, 0)});
    GPSTracker gpsTracker = new GPSTracker(MainActivity.this);

    TelephonyManager tm = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textTimeDisplay = findViewById(R.id.timeDisplay);
        buttonStartResume = findViewById(R.id.startButton);
        buttonStop = findViewById(R.id.stopButton);
        buttonSaveToCsv = findViewById(R.id.saveButton);
        final GraphView graph = findViewById(R.id.graph);

        initializeGraph(graph);
        requestPermission();

        buttonStartResume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRunning = true;
                buttonStartResume.setText("Start");
                startReadings();
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRunning = false;
                buttonStartResume.setText("Resume");
            }
        });

        buttonSaveToCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeDataToCSV();
            }
        });

    }

    private void startReadings() {
        timeCalculator();
        getNetworkParameters();
        getLocationData();
        prepareCSVData();
        appendDataToGraph();
        if (isRunning) {
            delayHandler(1000);
        }
    }

    private void delayHandler(int milliseconds) {
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                startReadings();
            }
        };
        handler.postDelayed(runnable, milliseconds);
    }

    private void timeCalculator() {
        timeCount++;
        int minutes = timeCount / 60;
        int seconds = timeCount % 60;
        String timeFormat = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        textTimeDisplay.setText(timeFormat);
    }

    private void getNetworkParameters() {
        String log = "";

        tm = (TelephonyManager) getSystemService(MainActivity.this.TELEPHONY_SERVICE);
        String networkOperator = tm.getNetworkOperatorName();
        Log.i(TAG, networkOperator);

        try {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Location Permissions not granted");
                return;
            }

            if (tm.getAllCellInfo() == null) {
                Log.i(TAG, "getAllCellInfo returned null");
            } else {
                List<CellInfo> data = tm.getAllCellInfo();
                Log.i(TAG, data.toString());
                for (final CellInfo infodata : data) {
                    if (infodata instanceof CellInfoLte) {

                        final CellSignalStrengthLte lte = ((CellInfoLte) infodata).getCellSignalStrength();
                        level = lte.getLevel(); //Retrieve an abstract level value for the overall signal quality
                        rsrp = lte.getRsrp();  //Get reference signal received power in dBm
                        rsrq = lte.getRsrq();  //Get reference signal received quality
                        rssnr = lte.getRssnr(); //Get reference signal signal-to-noise ratio
                        cqi = lte.getCqi();
                        asuLevel = lte.getAsuLevel();
                        timingAdvance = lte.getTimingAdvance();
                        dbm = lte.getDbm();

                        final CellIdentityLte lteCellInfo = ((CellInfoLte) infodata).getCellIdentity();
                        ci = lteCellInfo.getCi();
                        earFcn = lteCellInfo.getEarfcn();
                        tac = lteCellInfo.getTac();
                        pci = lteCellInfo.getPci();

                        log = " Operator: " + networkOperator + "\n Signal Quality: " + level + " dBm" + "\n RSRP: " + rsrp + " dBm" + "\n RSRQ: " + rsrq + " dBm" + " dBm" + "\n RSSNR: " + rssnr + "\n CQI: " + cqi + "\n ASU Level: " + asuLevel + "\n Timing Advance: " + timingAdvance + "\n dBm: " + dbm + "\n CI: " + ci + "\n Earfcn: " + earFcn + "\n TAC :" + tac + "\n PCI: " + pci;
                        Log.i(TAG, log);

                        if (isFirstOperatorDisplay) {
                            Toast.makeText(this, " Operator: " + networkOperator, Toast.LENGTH_LONG).show();
                            isFirstOperatorDisplay = false;
                        }

                    } else if (infodata instanceof CellInfoGsm) {
                        Log.i(TAG, "GSM Network Type");
                        notifyNetworkToUser("GSM");
                    } else if (infodata instanceof CellInfoCdma) {
                        Log.i(TAG, "CDMA Network Type");
                        notifyNetworkToUser("CDMA");
                    } else if (infodata instanceof CellInfoWcdma) {
                        Log.i(TAG, "WCDMA Network Type");
                        notifyNetworkToUser("WCDMA");
                    } else {
                        Log.i(TAG, "Unknown Network Type");
                        notifyNetworkToUser("Unknown");
                    }
                }
            }
        } catch (SecurityException e) {
            Log.i(TAG, "Exception");
            e.printStackTrace();
            Log.i(TAG, e.getMessage());
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, " Permissions: granted");
                    return;
                }
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
    }

    private void notifyNetworkToUser(String info) {
        Toast.makeText(getApplicationContext(), "Warning! " + info + " network detected", Toast.LENGTH_SHORT).show();
    }

    private void writeDataToCSV() {
        String folderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        File folder = new File(folderPath);
        timestamp = new Timestamp(System.currentTimeMillis());
        //String fileName = "NetworkData.csv";
        String fileName = timestamp.toString() + ".csv";
        if (!folder.exists()) {
            if (!folder.mkdir()) {
                Toast.makeText(getApplicationContext(), "Folder cannot be created", Toast.LENGTH_LONG).show();
                Log.i(TAG, "writeDataToCSV: File " + folderPath + " cannot be created");
            }
        } else {
            String csv = folderPath + "/" + fileName;
            try {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                    Log.i(TAG, "Write Permissions not granted");
                    return;
                }

                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 3);
                    Log.i(TAG, "Read Permissions not granted");
                    return;
                }

                CSVWriter csvWriter = new CSVWriter(new FileWriter(csv, true));
                if (isFirstCSVWrite) {
                    String[] headerNames = {"Systime", "RSRP(dBm)", "RSRQ(dB)", "RSSNR", "LATITUDE", "LONGITUDE","CQI", "ASULEVEL", "TIMINGADVANCE", "DBM", "CI", "EARFCN", "TAC", "PCI"};
                    csvWriter.writeNext(headerNames);
                    isFirstCSVWrite = false;
                }

                csvWriter.writeAll(networkDataList);
                csvWriter.close();
                Toast.makeText(getApplicationContext(), "Write data Successful to " + folderPath, Toast.LENGTH_LONG).show();
                Log.i(TAG, "writeDataToCSV: File " + folderPath + " Successful");

            } catch (IOException e) {
                e.printStackTrace();
                Log.i(TAG, "writeDataToCSV: Exception");
                Log.i(TAG, e.getMessage());
            }
        }
    }

    private void prepareCSVData() {
        timestamp = new Timestamp(System.currentTimeMillis());
        String[] networkData = {String.valueOf(timestamp), String.valueOf(rsrp), String.valueOf(rsrq), String.valueOf(rssnr), String.valueOf(latitude), String.valueOf(longitude), String.valueOf(cqi), String.valueOf(asuLevel), String.valueOf(timingAdvance), String.valueOf(dbm), String.valueOf(ci), String.valueOf(earFcn), String.valueOf(tac), String.valueOf(pci)};
        networkDataList.add(networkData);
    }

    private void initializeGraph(GraphView graph) {
        try {
            rsrpLine.setColor(Color.RED);
            rsrpLine.setTitle("RSRP");
            rsrqLine.setColor(Color.BLUE);
            rsrqLine.setTitle("RSRQ");
            rssnrLine.setColor(Color.MAGENTA);
            rssnrLine.setTitle("RSSNR");
            latitudeLine.setTitle("LATITUDE");
            latitudeLine.setColor(Color.LTGRAY);
            longitudeLine.setTitle("LONGITUDE");
            longitudeLine.setColor(Color.DKGRAY);
            graph.setTitle("Network parameters and GPS coordinates");
            graph.getLegendRenderer().setVisible(true);
            graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

            graph.addSeries(rsrpLine);
            graph.addSeries(rsrqLine);
            graph.addSeries(rssnrLine);
            graph.addSeries(latitudeLine);
            graph.addSeries(longitudeLine);

            graph.getViewport().setScrollable(true);
            graph.getViewport().setScrollableY(true);
            graph.getViewport().setScalable(true);
            graph.getViewport().setScalableY(true);


        } catch (IllegalArgumentException e) {
            Log.i(TAG, "initializeGraph: Exception" + e.getMessage());
        }
    }

    private void appendDataToGraph() {
        Log.i(TAG, "appendDataToGraph: " + timeCount + " " + rsrp + " " + rsrq + " " + rssnr + " " + latitude + " " + longitude);
        try {
            rsrpLine.appendData(new DataPoint(timeCount, rsrp), true, 10000);
            rsrqLine.appendData(new DataPoint(timeCount, rsrq), true, 10000);
            if (rssnr < 100)
                rssnrLine.appendData(new DataPoint(timeCount, rssnr), true, 10000);
            latitudeLine.appendData(new DataPoint(timeCount, latitude), true, 10000);
            longitudeLine.appendData(new DataPoint(timeCount, longitude), true, 10000);
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "run: Exception adding data to graph " + e.getMessage());
        }
    }

    private void getLocationData() {
        Location location = gpsTracker.getLocation();
        if (location != null) {
            latitude = location.getLatitude();
            longitude = location.getLongitude();
        } else {
            Log.i(TAG, "getLocationData: Location data not retrieved from device");
        }
        Log.i(TAG, "getLocationData: Latitude: " + latitude + " Longitude: " + longitude);
    }

}
