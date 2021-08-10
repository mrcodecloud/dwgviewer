package com.it22.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.groupdocs.cloud.viewer.api.ViewApi;
import com.groupdocs.cloud.viewer.model.requests.CreateViewRequest;
import com.it22.myapplication.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

import com.groupdocs.cloud.viewer.client.*;
import com.groupdocs.cloud.viewer.model.*;
import com.groupdocs.cloud.viewer.api.InfoApi;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private ActivityResultLauncher<Intent> someActivityResultLauncher;

    private String TAG = MainActivity.class.getName();
    String appSid = "74ca687c-6420-4ff0-aaec-b71de743ef6b";
    String appKey = "bb168bd73a11c3d1078f7820d93f10ec";
    ViewApi apiInstance;
//    Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        Configuration configuration = new Configuration(appSid, appKey);
        apiInstance = new ViewApi(configuration);
        someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            Uri uri = data.getData();
//                            String path = getRealPathFromURI(getApplicationContext(), uri);

                            Log.e("Check", "URI Path : " + uri.getPath());
//                            Log.e("Check", "Real Path : " + path);

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {

                                        FileInfo fileInfo = new FileInfo();
//                    fileInfo.setFilePath("SampleFiles/with_layers_and_layouts.dwg");
                                        fileInfo.setFilePath(uri.getPath());
                                        ViewOptions viewOptions = new ViewOptions();
                                        viewOptions.setFileInfo(fileInfo);
                                        viewOptions.setViewFormat(ViewOptions.ViewFormatEnum.HTML);
                                        HtmlOptions renderOptions = new HtmlOptions();
                                        CadOptions cadOptions = new CadOptions();
                                        cadOptions.addLayersItem("TRIANGLE");
                                        cadOptions.addLayersItem("QUADRANT");
                                        renderOptions.setCadOptions(cadOptions);
                                        viewOptions.setRenderOptions(renderOptions);

                                        ViewResult response = apiInstance.createView(new CreateViewRequest(viewOptions));

                                        Log.e(TAG,"RenderLayers completed: " + response.getPages().size());

                                    } catch (ApiException e) {
                                        Log.e(TAG, "Exception: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                }
                            }).start();

                        }
                    }
                });


        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("*/*");
        chooseFile = Intent.createChooser(chooseFile, "Choose a DWG file");
        someActivityResultLauncher.launch(chooseFile);




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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}