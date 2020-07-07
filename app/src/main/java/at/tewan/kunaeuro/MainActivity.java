package at.tewan.kunaeuro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import at.tewan.kunaeuro.model.ExchangeResult;

/**
 * @author Stefan Heinz
 */
public class MainActivity extends AppCompatActivity {

    public static final String KEY_KUNA_WORTH = "kuna_worth";
    public static final String KEY_LAST_FETCH = "last_fetch";

    public static final int REQUEST_CODE_PERMISSION = 423;

    private SharedPreferences sharedPreferences;

    private ConstraintLayout panelLoading;
    private ConstraintLayout panelResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getPreferences(MODE_PRIVATE);

        panelLoading = findViewById(R.id.panelLoading);
        panelResults = findViewById(R.id.panelResult);

    }

    public void onConvertButtonPressed(View view) {
        refreshExchange();
    }

    private void refreshExchange() {

        // If we don't have permissions, ask for them!
        if(checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.INTERNET}, REQUEST_CODE_PERMISSION);
            return;
        }

        panelResults.setVisibility(View.GONE);
        panelLoading.setVisibility(View.VISIBLE);
        new FetchExchangeAsyncTask().execute();
    }

    @SuppressLint("DefaultLocale")
    private void updateExchange(ExchangeResult result) {
        panelLoading.setVisibility(View.GONE);
        panelResults.setVisibility(View.VISIBLE);

        final String kunaCurrencyCode = "HRK";

        boolean fetchSuccessful = result != null && result.rates != null && result.rates.containsKey(kunaCurrencyCode);

        if(fetchSuccessful) {
            // Save the data we got to be the last fetched data
            final SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong(KEY_LAST_FETCH, System.currentTimeMillis());
            editor.putFloat(KEY_KUNA_WORTH, result.rates.get(kunaCurrencyCode)); // Won't produce NPE. Probably

            editor.apply();
        }

        final TextView exchangeRateLabel = findViewById(R.id.labelCurrencyExchange);
        final TextView resultLabel = findViewById(R.id.labelResult);
        boolean fromEuro = !((ToggleButton) findViewById(R.id.directionSwitch)).isChecked();

        final String sourceCurrency = fromEuro ? getString(R.string.euro) : getString(R.string.kuna);
        final String targetCurrency = fromEuro ? getString(R.string.kuna) : getString(R.string.euro);
        final float worth = sharedPreferences.getFloat(KEY_KUNA_WORTH, 1);
        final float exchange = (fromEuro ? worth : 1 / worth);

        exchangeRateLabel.setText(String.format("1 %s = %2.2f %s", sourceCurrency, exchange, targetCurrency));

        EditText baseValueView = findViewById(R.id.currencyInput);
        try {
            float baseValue = Float.parseFloat(baseValueView.getText().toString());

            resultLabel.setText(String.format("%10.2f %s", baseValue * exchange, targetCurrency));

            if(!fetchSuccessful) {
                resultLabel.append("\n" + getString(R.string.last_fetch) + new Date(sharedPreferences.getLong(KEY_LAST_FETCH, 0)).toString());
            }
        } catch (IllegalArgumentException e) {
            resultLabel.setText(R.string.invalid_input);
        }

    }

    private class FetchExchangeAsyncTask extends AsyncTask<Void, Void, ExchangeResult> {

        @Override
        protected ExchangeResult doInBackground(Void... nothings) {

            final String urlString ="https://api.exchangeratesapi.io/latest?base=EUR&symbols=HRK";

            try {
                final URL url = new URL(urlString);
                final HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.connect();

                if(urlConnection.getResponseCode() == 200) {

                    final BufferedReader reader = new BufferedReader(new InputStreamReader( urlConnection.getInputStream() ));
                    final StringBuilder result = new StringBuilder();
                    String line;
                    while((line = reader.readLine()) != null) {
                        result.append(line);
                    }

                    final Gson gson = new Gson();

                    return gson.fromJson(result.toString(), ExchangeResult.class);

                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(ExchangeResult result) {
            updateExchange(result);
        }
    }

}
