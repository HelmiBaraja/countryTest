package helmi.branded.me.myapplication;

import android.app.ProgressDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements HBUrlConnection.HBUrlListener {

    private List<Country> listCountries ;
    ProgressDialog dialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listCountries = new ArrayList<Country>();

         dialog = ProgressDialog.show(MainActivity.this, "",
                "Loading. Please wait...", true);

        HBUrlConnection urlConnection = new HBUrlConnection();
        urlConnection.listener = this;
        urlConnection.sendGet(Constant.RESTCOUNTRY_BASEURL);

    }

    @Override
    public void requestDidFinish(String output) {
        dialog.dismiss();
        try {
            JSONArray array = new JSONArray(output);

            for (int i =0; i< array.length() ; i++)
            {
                JSONObject object  = array.getJSONObject(i);
                String countryName = object.getString("name");
                String capital = object.getString("capital");
                String region = object.getString("region");
                String subRegion = object.getString("subregion");
                String demonym = object.getString("demonym");
                int population = object.getInt("population");

                JSONArray latlng = object.getJSONArray("latlng");
                double lat = (double) latlng.get(0);
                double lng = (double) latlng.get(1);

                Country country = new Country(countryName,capital,region,
                        subRegion,demonym,population,lat,lng);

//                Log.d("country", country.toString());
                listCountries.add(country);

            }




        } catch (JSONException e) {
            e.printStackTrace();
        }

        String capitalAlbania = listCountries.get(2).getCapital();
        Log.d("capital albania", capitalAlbania);

    }

    @Override
    public void requestDidFail(String output) {

        dialog.dismiss();
        Log.d("failed","failed="+output);
    }
}
