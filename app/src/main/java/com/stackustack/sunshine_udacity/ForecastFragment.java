package com.stackustack.sunshine_udacity;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

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
            FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
            fetchWeatherTask.execute("Poznan,pl");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        String[] examplesOfWeatherForTextView = {
                "Mon 6/23 - Sunny - 31/17",
                "Tue 6/24 - Foggy - 21/8",
                "Wed 6/25 - Cloudy - 22/17",
                "Thurs 6/26 - Rainy - 18/11",
                "Fri 6/27 - Foggy - 21/10",
                "Sat 6/28 - TRAPPED IN WEATHERSTATION - 23/18",
                "Sun 6/29 - Sunny - 20/7"
        };

        List<String> listOfExamplesForWeatherTextView = new ArrayList<String>(Arrays.asList(examplesOfWeatherForTextView));
        ArrayAdapter<String> forecastAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_forecast, listOfExamplesForWeatherTextView);

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(forecastAdapter);

        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private String getReadableDateAndTimeFromUnix(long timeInUnixFomatInSeconds) {
            Date timeInUnixFormatInMiliseconds = new Date(timeInUnixFomatInSeconds * 1000); //Java is expecting time in miliseconds, and since Unix format gives seconds we need to multiply by 1000
            SimpleDateFormat simpleFormatOfDayAndTime = new SimpleDateFormat("E d/MM/yy");
            return simpleFormatOfDayAndTime.format(timeInUnixFormatInMiliseconds).toString();
        }

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException {

            final String OWM_list = "list";
            final String OWM_weather = "weather";
            final String OWM_desciptionshort = "main";
            final String OWM_temperature = "temp";
            final String OWM_unitdatetime = "dt";


            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray listDayJson = forecastJson.getJSONArray(OWM_list);


            String[] resultStrs = new String[numDays];
            for(int i=0; i < listDayJson.length() ;i++) {
                String description;
                String highAndLowTemperature;
                String dayAndTime;

                JSONObject singleDayJson = listDayJson.getJSONObject(i);


                //wyciągniecie opisu (main) listArray[i].weatherArray[0].main
                JSONObject weatherForSingleDayJson = singleDayJson.getJSONArray(OWM_weather).getJSONObject(0);
                description = weatherForSingleDayJson.getString(OWM_desciptionshort);

                // wyciągniecie czasu w formacie "Mon 5/24" ->
                long dateAndTimeInUnixJson = singleDayJson.getLong(OWM_unitdatetime);
                dayAndTime = getReadableDateAndTimeFromUnix(dateAndTimeInUnixJson);


                // wyciągniecie tempMax/tempMin - listArray[i].tempObject.min/max
                JSONObject temperatureForSingleDayJson = singleDayJson.getJSONObject(OWM_temperature);
                double low = temperatureForSingleDayJson.getDouble("min");
                double high = temperatureForSingleDayJson.getDouble("max");
                highAndLowTemperature = high + "/" + low;

                resultStrs[i] = dayAndTime + " - " + description + " - " + highAndLowTemperature;
            }

            for(String s : resultStrs) {
                Log.v(LOG_TAG, "SUNSHINE single weather entry for day: " + s);
            }

            return resultStrs;
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
                Log.v(LOG_TAG, "SUNSHINE builtUri String: " + builtUri.toString());
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
                Log.v(LOG_TAG, "SUNSHINE Forecast JSON string: " + forecastJsonStr);

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
    }
}
