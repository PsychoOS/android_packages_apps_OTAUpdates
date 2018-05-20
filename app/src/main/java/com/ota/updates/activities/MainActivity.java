/*
 * Copyright (C) 2015 Matt Booth (Kryten2k35).
 * Copyright (C) 2017 The halogenOS Project.
 *
 * Licensed under the Attribution-NonCommercial-ShareAlike 4.0 International
 * (the "License") you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://creativecommons.org/licenses/by-nc-sa/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ota.updates.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.ota.updates.R;
import com.ota.updates.RomUpdate;
import com.ota.updates.tasks.Changelog;
import com.ota.updates.tasks.LoadUpdateManifest;
import com.ota.updates.utils.Constants;
import com.ota.updates.utils.Preferences;
import com.ota.updates.utils.Utils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements Constants{

    public final String TAG = this.getClass().getSimpleName();

    private Context mContext;

    private Builder mCompatibilityDialog;
    private Builder mDonateDialog;
    private Builder mPlayStoreDialog;

    public ProgressBar mProgressBar;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(MANIFEST_LOADED)) {
                // Reloads layouts to reflect the updated manifest information
                updateDonateLinkLayout();
                updateAddonsLayout();
                updateRomInformation();
                updateRomUpdateLayouts();
                updateWebsiteLayout();
            }
        }
    };

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0)
        {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        mContext = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar3);
        setSupportActionBar(toolbar);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        toolbar.setPadding(0, getStatusBarHeight(), 0, 0);
        isStoragePermissionGranted();
        boolean firstRun = Preferences.getFirstRun(mContext);
        if(firstRun) {
            Preferences.setFirstRun(mContext, false);
            showWhatsNew();
        }

        if (getSupportActionBar() != null)
        {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        String oldChangelog = Preferences.getOldChangelog(mContext);
        String currentChangelog = getResources().getString(R.string.app_version);
        if(!oldChangelog.equals(currentChangelog)) {
            showWhatsNew();
        }

        // Create download directories if needed
        File installAfterFlashDir = new File(SD_CARD
                + File.separator
                + OTA_DOWNLOAD_DIR
                + File.separator
                + INSTALL_AFTER_FLASH_DIR);
        if (!installAfterFlashDir.mkdirs()) Log.e(TAG,"Download directory creation failed");


        // Check the correct build prop values are installed
        // Also executes the manifest/update check
        if (!Utils.isConnected(mContext)) {
            Builder notConnectedDialog = new Builder(mContext);
            notConnectedDialog.setTitle(R.string.main_not_connected_title)
            .setMessage(R.string.main_not_connected_message)
            .setPositiveButton(R.string.ok, new OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ((Activity) mContext).finish();
                }
            })
            .setCancelable(false)
            .show();
        } else {
            new CompatibilityTask(mContext).execute();
        }

        // Has the download already completed?
        Utils.setHasFileDownloaded(mContext);

        // Update the layouts
        updateDonateLinkLayout();
        updateAddonsLayout();
        updateRomInformation();
        updateRomUpdateLayouts();
        updateWebsiteLayout();

    }
    public  boolean isStoragePermissionGranted() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        this.registerReceiver(mReceiver, new IntentFilter(MANIFEST_LOADED));
    }

    @Override
    public void onStop() {
        super.onStop();
        this.unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.ota_menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
            switch (item.getItemId()) {
            case R.id.menu_changelog:
                openChangelog(null);
                return true;
            case R.id.menu_settings:
                openSettings(null);
                return true;
            case android.R.id.home :
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
            }
    }


    private void updateRomUpdateLayouts() {
        View updateNotAvailable;
        FloatingActionButton down_fab = (FloatingActionButton) findViewById(R.id.layout_main_update_available);
        updateNotAvailable = findViewById(R.id.layout_main_no_update_available);
        updateNotAvailable.setVisibility(View.GONE);
        down_fab.setVisibility(View.GONE);
        TextView updateAvailableSummary = (TextView) findViewById(R.id.main_tv_update_available_summary);
        TextView updateNotAvailableSummary = (TextView) findViewById(R.id.main_tv_no_update_available_summary);

        mProgressBar = (ProgressBar) findViewById(R.id.bar_main_progress_bar);
        mProgressBar.setVisibility(View.GONE);

        // Update is available
        if (RomUpdate.getUpdateAvailability(mContext) ||
                (!RomUpdate.getUpdateAvailability(mContext)) && Utils.isUpdateIgnored(mContext)) {
            updateNotAvailable.setVisibility(View.GONE);
            down_fab.setVisibility(View.VISIBLE);
            TextView updateAvailableTitle = (TextView) findViewById(R.id.main_tv_update_available_title);

            if (Objects.equals(Utils.getProp("ro.psycho.version"), RomUpdate.getVersionName(mContext))) {
                down_fab.setVisibility(View.GONE);
            }

            if (Preferences.getDownloadFinished(mContext)) { //  Update already finished?
                updateAvailableTitle.setText(getResources().getString(R.string.main_update_finished));
                String updateSummary = RomUpdate.getVersionName(mContext)
                        + "\n\n"
                        + getResources().getString(R.string.main_download_completed_details);
                updateAvailableSummary.setText(updateSummary);
            } else if (Preferences.getIsDownloadOnGoing(mContext)) {
                updateAvailableTitle.setText(getResources().getString(R.string.main_update_progress));
                mProgressBar.setVisibility(View.VISIBLE);
                String updateSummary = getResources().getString(R.string.main_tap_to_view_progress);
                updateAvailableSummary.setText(updateSummary);
            } else {
                updateAvailableTitle.setText(getResources().getString(R.string.main_update_available));
                String updateSummary = RomUpdate.getVersionName(mContext)
                        + "\n\n"
                        + getResources().getString(R.string.main_tap_to_download);
                updateAvailableSummary.setText(updateSummary);
            }
        } else {
            updateNotAvailable.setVisibility(View.VISIBLE);

            boolean is24 = DateFormat.is24HourFormat(mContext);
            Date now = new Date();
            Locale locale = Locale.getDefault();
            String time;

            if (is24) {
                time = new SimpleDateFormat("d, MMMM HH:mm", locale).format(now);
            } else {
                time = new SimpleDateFormat("d, MMMM hh:mm a", locale).format(now);
            }

            Preferences.setUpdateLastChecked(this, time);
            String lastChecked = getString(R.string.main_last_checked);
            updateNotAvailableSummary.setText(lastChecked + " " + time);
        }
    }

    private void updateAddonsLayout() {
        CardView addonsLink = (CardView) findViewById(R.id.layout_main_addons);
        addonsLink.setVisibility(View.GONE);

        if (RomUpdate.getAddonsCount(mContext) > 0) {
            addonsLink.setVisibility(View.VISIBLE);
        }
    }

    private void updateDonateLinkLayout() {
        CardView donateLink = (CardView) findViewById(R.id.layout_main_dev_donate_link);
        donateLink.setVisibility(View.GONE);

        if (!(RomUpdate.getDonateLink(mContext).trim().equals("null"))) {
            donateLink.setVisibility(View.VISIBLE);
        }
    }

    private void updateWebsiteLayout() {
        CardView webLink = (CardView) findViewById(R.id.layout_main_dev_website);
        webLink.setVisibility(View.GONE);

        if (!RomUpdate.getWebsite(mContext).trim().equals("null")) {
            webLink.setVisibility(View.VISIBLE);
        }
    }

    private void updateRomInformation() {

        //ROM name
        TextView romName = (TextView) findViewById(R.id.tv_main_rom_name);
        String romNameActual = Utils.getProp("ro.psycho.build.version");
        String OurRomName = "Psycho OS";
        if (romNameActual == null) {
            romNameActual = "Unknown";
            OurRomName = "null";
        }
        
        String getDevice, Brand;
        Brand = Utils.getProp("ro.product.brand");
        getDevice = Utils.getProp("ro.product.model");
        romName.setText(String.format(getString(R.string.main_rom_device), Brand + " " + getDevice));

        //ROM version
        TextView romVersion = (TextView) findViewById(R.id.tv_main_rom_version);
        romVersion.setText(String.format(getString(R.string.main_rom_version), OurRomName + " v" + romNameActual));

        //ROM date
        TextView romDate = (TextView) findViewById(R.id.tv_main_rom_date);
        String romDateActual = Utils.getProp("ro.build.date");
        romDate.setText(String.format(getString(R.string.main_rom_build_date), romDateActual));

        //ROM android version
        TextView romAndroid = (TextView) findViewById(R.id.tv_main_android_version);
        String romAndroidActual = Utils.getProp("ro.build.version.release");
        romAndroid.setText(String.format(getString(R.string.main_android_version), romAndroidActual));

        //ROM Maintainer
        TextView romDeveloper = (TextView) findViewById(R.id.tv_main_rom_developer);
        boolean showDevName = !RomUpdate.getDeveloper(this).equals("null");
        romDeveloper.setVisibility(showDevName? View.VISIBLE : View.GONE);
        String romDeveloperActual = RomUpdate.getDeveloper(this);
        romDeveloper.setText(String.format(getString(R.string.main_rom_maintainer), romDeveloperActual));

    }

    public void openCheckForUpdates(View v) {
        new LoadUpdateManifest(mContext, true).execute();
    }

    public void openDownload(View v) {
        Intent intent = new Intent(mContext, AvailableActivity.class);
        startActivity(intent);
    }

    public void openAddons(View v) {
        Intent intent = new Intent(mContext, AddonActivity.class);
        startActivity(intent);
    }

    public void openDonationPage(View v) {

        boolean payPalLinkAvailable = RomUpdate.getDonateLink(mContext).trim().equals("null");
        if (!payPalLinkAvailable) {
            mDonateDialog.show();
        } else {
            String url = RomUpdate.getDonateLink(mContext);
            if (url != null){
                final Uri uri = Uri.parse(url);
                if (uri.getScheme() == null){
                    url = "http://" + url;
                }
            } else {
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        }
    }

    public void openWebsitePage(View v) {
        String url = RomUpdate.getWebsite(mContext);
        if (url != null){
            final Uri uri = Uri.parse(url);
            if (uri.getScheme() == null){
                url = "http://" + url;
            }
        } else {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    public void openSettings(View v) {
        Intent intent = new Intent(mContext, SettingsActivity.class);
        startActivity(intent);
    }

    public void openChangelog (View v) {
        String title = getResources().getString(R.string.changelog);
        String changelog = RomUpdate.getChangelog(mContext);
        new Changelog(this, mContext, title, changelog, false).execute();
    }

    private void showWhatsNew() {
        String title = getResources().getString(R.string.changelog);
        String changelog = getResources().getString(R.string.changelog_url);
        new Changelog(this, mContext, title, changelog, true).execute();
    }

    private class CompatibilityTask extends AsyncTask<Void, Boolean, Boolean> implements Constants{

        public final String TAG = this.getClass().getSimpleName();

        private Context mContext;
        private String mPropName;

        CompatibilityTask(Context context) {
            mContext = context;
            mPropName = Constants.OTA_MANIFEST;
        }

        @Override
        protected Boolean doInBackground(Void... v) {
            return Utils.doesPropExist(mPropName);
        }

        @Override
        protected void onPostExecute(Boolean result) {

            if (result) {
                if (DEBUGGING)
                    Log.d(TAG, "Prop found");
                new LoadUpdateManifest(mContext, true).execute();
            }
            super.onPostExecute(result);
        }
    }
}
