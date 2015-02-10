package com.stackustack.sunshine_udacity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        mForecastAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_forecast, new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecastDataForDetailActivity = mForecastAdapter.getItem(position);
                Intent showDetails = new Intent(getActivity(), DetailActivity.class).putExtra(Intent.EXTRA_TEXT, forecastDataForDetailActivity);
                startActivity(showDetails);
            }
        });

        return rootView;
    }

    private void updateWeather() {
        FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String locationFromPrefs = prefs.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));

        fetchWeatherTask.execute(locationFromPrefs);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }



    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private String getReadableMinMaxTemperatureRoundedFromSingleDayJson(JSONObject singleDayJson) throws JSONException {
            final String OWM_temperature = "temp";
            final String OWM_maxtemperature = "max";
            final String OWM_mintemperature = "min";

            JSONObject temperatureForSingleDayJson = singleDayJson.getJSONObject(OWM_temperature);
            double lowestTemperature = temperatureForSingleDayJson.getDouble(OWM_mintemperature);
            double highestTemperature = temperatureForSingleDayJson.getDouble(OWM_maxtemperature);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = prefs.getString(getString(R.string.pref_units_key), getString(R.string.pref_units_metric));

            if (unitType.equals(getString(R.string.pref_units_imperial))) {
                lowestTemperature = (lowestTemperature * 1.8) +32 ;
                highestTemperature = (highestTemperature * 1.8) + 32;
            } else if (!unitType.equals(getString(R.string.pref_units_metric))) {
                Log.d(LOG_TAG, "SUNISHINE Unit type not found: " + unitType);
            }

            long lowestTemperatureRounded = Math.round(lowestTemperature);
            long highestTemperatureRounded = Math.round(highestTemperature);

            return lowestTemperatureRounded + " / " + highestTemperatureRounded;
        }

        private String getReadableDateAndTimeFromSingleDayJson(JSONObject singleDayJson) throws JSONException {
            final String OWM_UnixTimeFromJson = "dt";

            long unixTime = singleDayJson.getLong(OWM_UnixTimeFromJson);
            Date timeInUnixFormatInMiliseconds = new Date(unixTime*1000);
            SimpleDateFormat simpleFormatOfDayAndTime = new SimpleDateFormat("E, d/MM/yy");
            String readableDayAndTimeLowercase = simpleFormatOfDayAndTime.format(timeInUnixFormatInMiliseconds);

            return Character.toUpperCase(readableDayAndTimeLowercase.charAt(0)) + readableDayAndTimeLowercase.substring(1);
        }

        private String getWeatherShortInfoFromSingleDayJson(JSONObject singleDayJson) throws JSONException {
            final String OWM_weatherInfo = "weather";
            final String OWM_weatherShortStatus = "main";

            return singleDayJson.getJSONArray(OWM_weatherInfo).getJSONObject(0).getString(OWM_weatherShortStatus);
        }

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException {
            final String OWM_listOfDays = "list";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray listOfDayJson = forecastJson.getJSONArray(OWM_listOfDays);
            String[] resultWeatherForecastString = new String[numDays];

            for(int i=0; i < listOfDayJson.length() ;i++) {
                JSONObject singleDayJson = listOfDayJson.getJSONObject(i);
                resultWeatherForecastString[i] = getReadableDateAndTimeFromSingleDayJson(singleDayJson) + " - " + getWeatherShortInfoFromSingleDayJson(singleDayJson) + " - " + getReadableMinMaxTemperatureRoundedFromSingleDayJson(singleDayJson);
            }

            for(String s : resultWeatherForecastString) {
                Log.v(LOG_TAG, "SUNSHINE single weather entry for day: " + s);
            }

            return resultWeatherForecastString;
        }

        @Override
        protected String[] doInBackground(String... params) {
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            String format = "JSON";
            String units = "metric";
            int numDays = 7;

            try {
                final String FORECAST_URL_BASE = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";

                Uri builtUri = Uri.parse(FORECAST_URL_BASE).buildUpon()
                        .appendQueryParameter(QUERY_PARAM,params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM,units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .build();
                Log.v(LOG_TAG, "SUNSHINE builtUri String: " + builtUri.toString()); // Logging the built Url using Uri method
                URL url = new URL(builtUri.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                forecastJsonStr = buffer.toString();
                //Log.v(LOG_TAG, "SUNSHINE Forecast JSON string: " + forecastJsonStr); // Logging the full JSON string from server

            } catch (IOException e) {
                Log.e(LOG_TAG, "SUNSHINE Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (result !=null) {
                mForecastAdapter.clear();
                for(String dayForecastStr : result)
                    mForecastAdapter.add(dayForecastStr);
            }
        }
    }
}
