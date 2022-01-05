package com.polar.nextcloudservices;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.polar.nextcloudservices.settings.SettingsUtils.getBoolPreference;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Menu;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.core.view.GravityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException;
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException;
import com.nextcloud.android.sso.model.SingleSignOnAccount;
import com.nextcloud.android.sso.ui.UiExceptionManager;
import com.polar.nextcloudservices.Preferences.PreferencesUtils;
import com.polar.nextcloudservices.databinding.ActivityMainBinding;
import com.polar.nextcloudservices.settings.NotificationServiceConnection;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private NotificationServiceConnection mServiceConnection = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_mainview, R.id.nav_credits, R.id.nav_connection_settings)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        startNotificationService();
        setupToolbars();
        updateNotificationServiceStatus();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }


    public void stopNotificationService() {
        if(isNotificationServiceRunning()) {
            Log.i(TAG, "Stopping service");
            Context context = getApplicationContext();
            context.stopService(new Intent(context, NotificationService.class));
        }
    }
    public void startNotificationService() {
        ///--------
        //Log.d(TAG, "startService: ENTERING");
        if (!isNotificationServiceRunning() && getBoolPreference(this, "enable_polling",true)) {
            Log.d(TAG, "Service is not running: creating intent to start it");
            startService(new Intent(getApplicationContext(), NotificationService.class));
        }
    }

    private boolean isNotificationServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<?> services = manager.getRunningServices(Integer.MAX_VALUE);
        return (services.size() > 0);
    }

    private void updateNotificationServiceStatus() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        if (services.size() == 0) {
            Log.e(TAG, "Service is not running!");
        } else if(mServiceConnection==null){
            mServiceConnection = new NotificationServiceConnection();
            bindService(new Intent(getApplicationContext(), NotificationService.class), mServiceConnection, 0);
        } else {
            mServiceConnection.updateConnectionStateIndicator(binding);
        }
    }

    /**
     * Taken from package it.niedermann.owncloud.notes.main.MainActivity
     * https://github.com/stefan-niedermann/nextcloud-notes
     *
     */
    private void setupToolbars() {
        setSupportActionBar(binding.appBarMain.toolbar);
        try {
            updateProfileImage();
        } catch (NextcloudFilesAppAccountNotFoundException | NoCurrentAccountSelectedException e) {
            e.printStackTrace();
        }
        binding.appBarMain.homeToolbar.setOnClickListener((v) -> {
            if (binding.appBarMain.toolbar.getVisibility() == GONE) {
                mServiceConnection.updateConnectionStateIndicator(binding);
            }
        });

        binding.appBarMain.launchAccountSwitcher.setOnClickListener((v) -> openAccountChooser());
        binding.appBarMain.menuButton.setOnClickListener((v) -> binding.drawerLayout.openDrawer(GravityCompat.START));


        binding.appBarMain.searchView.setOnCloseListener(() -> {
            if (binding.appBarMain.toolbar.getVisibility() == VISIBLE && TextUtils.isEmpty(binding.appBarMain.searchView.getQuery())) {
                mServiceConnection.updateConnectionStateIndicator(binding);
                return true;
            }
            return false;
        });
        binding.appBarMain.connectionState.setOnClickListener((v) -> mServiceConnection.updateConnectionStateIndicator(binding));

    }

    private void updateProfileImage() throws NextcloudFilesAppAccountNotFoundException, NoCurrentAccountSelectedException {
        if (!PreferencesUtils.getBoolPreference(this, "sso_enabled", false)) {
            return;
        }
        // If you stored the "default" account using setCurrentAccount(…) you can get the account by using the following line:
        SingleSignOnAccount ssoAccount = AccountImporter.getSingleSignOnAccount(this, PreferencesUtils.getPreference(this, "sso_name"));

        GlideUrl url = new GlideUrl(ssoAccount.url+"/index.php/avatar/" + ssoAccount.userId + "/64");

        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_account_circle_grey_24dp)
                .error(R.drawable.ic_account_circle_grey_24dp)
                .apply(RequestOptions.circleCropTransform())
                .into(binding.appBarMain.launchAccountSwitcher);

    }

    private void openAccountChooser() {
        try {
            AccountImporter.pickNewAccount(this);
            try {
                updateProfileImage();
            } catch (NextcloudFilesAppAccountNotFoundException | NoCurrentAccountSelectedException e) {
                e.printStackTrace();
            }
        } catch (NextcloudFilesAppNotInstalledException | AndroidGetAccountsPermissionNotGranted e) {
            UiExceptionManager.showDialogForException(getApplicationContext(), e);
        }
    }
}